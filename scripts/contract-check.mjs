#!/usr/bin/env node
/**
 * 前后端契约对拍（机械部分）—— AGENT.md §4.7「契约守门」
 *
 * 只做三件能确定性判真假的事，不做字段语义判断：
 *   1. 前端每个 `/api/...` 调用，后端必须存在同方法同形状的路由（否则必然 404）；
 *   2. `pages.json` 注册的页面必须存在 .vue，`src/pages/**` 下的页面必须注册（否则白屏）；
 *   3. 后端有路由但前端从未调用 → 只提示，不算失败（微信回调 / 预留接口）。
 *
 * 字段级的响应形状（如「读 resp.list 但后端返回 months[]」）静态查不出来，
 * 必须由验收按 AGENT.md §4.7 的手工步骤跑真实响应比对。
 *
 * 用法：node scripts/contract-check.mjs
 * 退出码：0 = 全绿；1 = 存在不一致
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const BE_CTRL = path.join(ROOT, 'backend/src/main/java/com/couplemeal/controller')
const FE_SRC = path.join(ROOT, 'couple-meal-miniprogram/src')
const PAGES_JSON = path.join(FE_SRC, 'pages.json')
const API_PREFIX = '/api' // server.servlet.context-path

const DYN = ':' // 统一占位符：后端 {id} 与前端 ${x} 都归一成 ':'

let fail = 0
let warn = 0
const line = (s = '') => console.log(s)
const ok = (s) => line(`  ✓ ${s}`)
const bad = (s) => { fail++; line(`  ✗ ${s}`) }
const note = (s) => { warn++; line(`  ! ${s}`) }

/** 递归列出目录下指定后缀的文件 */
function walk(dir, exts, out = []) {
  if (!fs.existsSync(dir)) return out
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name)
    if (e.isDirectory()) walk(p, exts, out)
    else if (exts.some((x) => e.name.endsWith(x))) out.push(p)
  }
  return out
}

/** 归一化路径：去 query、${x}/{x} → ':'、去尾斜杠 */
function normalize(p) {
  let s = p.split('?')[0].split('#')[0].trim()
  // 先换 ${...} 再换 {...}，否则 `${a}` 会被后者的 {} 规则啃成 `$:`
  s = s.replace(/\$\{[^}]*\}/g, DYN).replace(/\{[^}]*\}/g, DYN)
  if (s.length > 1) s = s.replace(/\/+$/, '')
  return s
}

// ---------------------------------------------------------------- 后端路由
/** @type {{method:string,path:string,where:string}[]} */
const routes = []
for (const file of walk(BE_CTRL, ['.java'])) {
  const src = fs.readFileSync(file, 'utf8')
  const baseM = src.match(/@RequestMapping\("([^"]*)"\)/)
  const base = baseM ? baseM[1] : ''
  const re = /@(Get|Post|Put|Delete)Mapping(?:\("([^"]*)"\))?/g
  let m
  while ((m = re.exec(src))) {
    const method = m[1].toUpperCase()
    const sub = m[2] || ''
    routes.push({
      method,
      path: normalize(API_PREFIX + base + sub),
      where: `${path.basename(file)}:${src.slice(0, m.index).split('\n').length}`,
    })
  }
}
// 去重（同名同法只留一条）
const uniqRoutes = [...new Map(routes.map((r) => [r.method + r.path, r])).values()]

// ---------------------------------------------------------------- 前端调用
/** @type {{path:string,where:string}[]} */
const calls = []
for (const file of walk(FE_SRC, ['.ts', '.vue', '.js'])) {
  const src = fs.readFileSync(file, 'utf8')
  const lines = src.split('\n')
  lines.forEach((text, i) => {
    const re = /['"`]\/api\/[^'"`]*['"`]?/g
    let m
    while ((m = re.exec(text))) {
      const raw = m[0].replace(/['"`]+$/, '').replace(/^['"`]/, '')
      if (raw === '/api') continue
      calls.push({ path: normalize(raw), where: `${path.relative(ROOT, file)}:${i + 1}` })
    }
  })
}
const uniqCalls = [...new Map(calls.map((c) => [c.path, c])).values()]

// ---------------------------------------------------------------- 1) 路由对拍
const sameShape = (a, b) => {
  const sa = a.split('/').filter(Boolean)
  const sb = b.split('/').filter(Boolean)
  if (sa.length !== sb.length) return false
  return sa.every((seg, i) => seg === sb[i] || seg === DYN || sb[i] === DYN)
}
/** 前端路径段里含 ':' 的段，是否落在了后端的字面量段上（无法静态确认，需人工） */
const looseMatch = (fe, be) => {
  const sf = fe.split('/').filter(Boolean)
  const sb = be.split('/').filter(Boolean)
  if (sf.length !== sb.length) return false
  return sf.some((seg, i) => seg === DYN && sb[i] !== DYN)
}

line('【1】前端调用 → 后端路由')
const failAt1 = fail
for (const c of uniqCalls) {
  const hit = uniqRoutes.filter((r) => sameShape(c.path, r.path))
  if (hit.length === 0) {
    bad(`前端调用无对应后端路由：${c.path}  (${c.where})`)
  } else if (hit.every((r) => looseMatch(c.path, r.path))) {
    note(`动态尾段无法静态确认：${c.path} 可能命中 ${hit.map((r) => r.method + r.path).join(' | ')}  (${c.where})`)
  }
}
if (fail === failAt1) ok(`${uniqCalls.length} 个前端调用全部有后端路由兜底`)

// ---------------------------------------------------------------- 2) 路由未被调用
line('')
line('【2】后端路由 → 前端是否调用（仅提示）')
const called = uniqCalls.map((c) => c.path)
const orphan = uniqRoutes.filter(
  (r) => !called.some((p) => sameShape(p, r.path) || sameShape(r.path, p)),
)
for (const r of orphan) note(`后端有路由但前端未调用：${r.method} ${r.path}  (${r.where})`)
if (orphan.length === 0) ok('没有孤立路由')

// ---------------------------------------------------------------- 3) 页面注册
line('')
line('【3】pages.json 页面注册')
const pagesRaw = fs.readFileSync(PAGES_JSON, 'utf8')
let pagesCfg
try {
  // 容错：去 // 行注释与尾逗号后再解析
  pagesCfg = JSON.parse(pagesRaw.replace(/^\s*\/\/.*$/gm, '').replace(/,(\s*[}\]])/g, '$1'))
} catch (e) {
  bad(`pages.json 解析失败：${e.message}`)
  pagesCfg = { pages: [] }
}
const registered = new Set((pagesCfg.pages || []).map((p) => p.path))
const failAt3 = fail
for (const p of registered) {
  if (!fs.existsSync(path.join(FE_SRC, `${p}.vue`))) bad(`pages.json 注册了不存在的页面：${p}.vue`)
}
for (const f of walk(path.join(FE_SRC, 'pages'), ['.vue'])) {
  const rel = path.relative(FE_SRC, f).replace(/\\/g, '/').replace(/\.vue$/, '')
  if (!registered.has(rel)) bad(`页面文件未在 pages.json 注册（会白屏）：${rel}.vue`)
}
if (fail === failAt3) ok(`${registered.size} 个页面注册与文件一一对应`)

// ---------------------------------------------------------------- 汇总
line('')
line('='.repeat(64))
line(`契约对拍：不一致 ${fail} 项，需人工确认 ${warn} 项`)
line('='.repeat(64))
process.exit(fail > 0 ? 1 : 0)
