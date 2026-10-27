---
name: frontend-coding-standards
description: 前端编码规范（V1.5）。编写或评审前端代码时遵守：文件与目录规范、HTML/CSS/JS/TS 写法、命名与注释、安全编码、lint 校验、组件开发规范、npm 包管理。本项目前端为微信小程序（WXML/WXSS/JS），通用条款适用，Vue3 相关章节仅作参考。
---

# 前端编码规范

> 版本：V1.5　|　适用范围：本项目前端（微信小程序）

---

## 变更日志

| 编号 | 版本 | 修改内容 | 日期 |
| --- | --- | --- | --- |
| 1 | V1.0 | 新建 | 2021.11.5 |
| 2 | V1.1 | 根据第一次评审决议进行修改与补充 | 2021.11.19 |
| 3 | V1.3 | 增加安全编码章节，结合评审意见修改 | 2021.12.14 |
| 4 | V1.4 | 增加 css 样式引用，代码 lint 校验，npm 包管理规范，组件开发规范 | 2023.11.16 |
| 5 | V1.5 | 更新部分规范、去除废弃规范 | 2026.05.15 |

---

## 简介

### 背景和目的

在一款软件的生命周期中，日常维护的时间成本将占整个生命周期的百分之八十。遵守代码规约，代码风格保持一致，提升代码的可读性，缩短维护所花费的时间。除此之外，统一的代码风格可以让新入手的人更顺畅地熟悉整个工程，不会因为风格迥异的代码造成不必要的困惑。

本文档的主要目的是希望规范前端开发的代码编码风格，尽量通过代码规范降低系统的维护成本。

本文档主要融合了阿里、Google 等企业与团队的开发规范，结合项目实际情况以及个人的日常总结思考。大家可以共同讨论，得出最符合当前状态的结论。

### 适用人群

本文档的适用人员范围包括涉及前端开发的相关技术人员。

### 术语和缩略语

| 名词 | 解释 |
| --- | --- |
| 【强制】 | 必须遵守的规范 |
| 【推荐】 | 大部分情况需要遵守的规范 |
| 【参考】 | 建议遵守 |

---

## 具体规范

## 文件规范

### 目录命名

- 【**强制**】**目录（文件夹）全部采用小写，以中划线（`-`）分隔。**

  正例：

  ```text
  src/components/layout/config/
  src/router/modules/
  src/views/agent/
  src/utils/safe/
  ```

  反例：

  ```text
  src/components/layoutConfig/
  src/routerModules/
  src/views/AgentManage/
  ```

### 文件命名

- 【**强制**】**JS / TS / Vue / Less / Scss / Css 文件采用小驼峰（lowerCamelCase）。**

  正例：

  ```text
  useAside.ts
  request.config.ts
  home.ts
  agentList.vue
  global.scss
  ```

  反例：

  ```text
  use-aside.ts
  request-config.ts
  Home.ts
  agent-list.vue
  ```

- 【**强制**】**Vue 页面入口组件可使用大驼峰（PascalCase）以突出页面属性，但对应的路由模块文件仍使用小驼峰。**

  正例：

  ```text
  src/views/agent/AgentList.vue
  src/router/modules/agent.ts
  ```

  反例：

  ```text
  src/views/agent/agent-list.vue
  src/router/modules/Agent.ts
  ```

- 【**强制**】**常量 / 枚举文件采用全大写、下划线分隔（UPPER_SNAKE_CASE）。**

  正例：

  ```text
  PERMISSION.ts
  HTTP_STATUS.ts
  ```

  反例：

  ```text
  permission.ts
  httpStatus.ts
  ```

- 【**强制**】**图片等用于页面渲染的静态资源按照图片具体负责内容编写名称，由目录区分功能模块。**

  正例：

  ```text
  src/assets/img/repair/titleIcon.png
  ```

  反例：

  ```text
  src/assets/img/repair-title-icon.png
  ```

- 【**强制**】**除地点、单位、人员等名称外，其他文件名全部使用英文单词拼接的方式，不使用中文缩写或者中英文混搭的方式。**

  正例：

  ```text
  repairLocationForm.ts
  ```

  反例：

  ```text
  bxLocationForm.ts
  ```

  例外：因业务面向的客户中单位、机构较多，以医院的名字举例，如杭州九院，这类名称用英文拼写冗长：hangzhou-ninth-hospital，因此地点、单位、人员等名称推荐使用简写 hzninth 或 hzjy。

### 项目 / 仓库命名

- 【**强制**】**项目 / 仓库名全部采用小写，以中划线分隔。**

  正例：

  ```text
  ts-agent-template
  business-big-screen
  cockpit-manage-system
  ```

  反例：

  ```text
  tsAgentTemplate
  business_big-screen
  ```

## HTML 编写规范

### 类命名

【**强制**】**命名规则为每个逻辑独立的功能模块最外层容器类名为功能相关名称，类名使用小写字母，以中划线分隔。**

正例：

```html
<div class="human-manager">
  <div class="container">
    <div class="name"></div>
    <div class="age"></div>
    ...
  </div>
</div>
```

反例：

```html
<div class="main">
  <div class="topdiv">
    <div class="child1"></div>
    <div class="child2"></div>
    ...
  </div>
</div>
```

### 行首缩进

【**强制**】**缩进使用一个 tab，即 2 个空格，嵌套逐级缩进。**

说明：业内对于缩进 1tab（2 空格）或者 2tab（4 空格）没有一个明确的结论，相当于在结构性与可读性间摇摆，这里之所以推荐 1tab，因为团队普遍使用轻薄笔记本电脑，屏幕较小，在缩进较大的时候浏览页面需要左右拖拽比较费力。

### 语义化标签

【**参考**】**尽量使用语义化标签。**

正例：

```html
<title></title>
<header></header>
<em></em>
```

反例：

```html
<div></div>
<div></div>
<div></div>
```

说明：代码结构清晰，方便阅读，有利于团队合作开发；方便其他设备解析以语义的方式来渲染网页。

### 标签嵌套

【**推荐**】**每一个块状元素应当独立一行，行内元素可根据具体情况抉择；同时段落元素禁止嵌套块状元素。**

正例：

```html
<h1><span></span></h1>
<p><span></span><span></span></p>
```

反例：

```html
<h1><div></div></h1>
<p><div></div><div></div></p>
```

说明：合理的嵌套规则能够提高代码可读性，上述嵌套规则基本算是业内默认的，让我们优雅的编写 HTML 吧。

### 引号的使用

【**推荐**】**元素属性值最外侧优先使用双引号而非单引号。**

正例：

```html
<input type="text">
<input type="radio" name="name" checked="checked">
```

反例：

```html
<input type=text>
<input type='text'>
<input type="radio" name="name" checked>
```

说明：统一标准，提升代码可读性的，不易给人产生歧义。

### 省略参数

【**参考**】**省略参数的配置项都应该优先考虑省略参数。**

正例：

```html
<input type="text" checked>
```

反例：

```html
<input type="radio" :checked="true">
```

### HTML 注释

- 【**推荐**】单行注释内容前后各一个空格字符，注释位于要注释代码的上面，单独占一行；
- 【**推荐**】模块注释前后各一个空格字符，`<!-- S Comment Text -->` 表示模块开始，`<!-- E Comment Text -->` 表示模块结束，模块与模块之间相隔一行。

正例：

```html
<!-- Comment Text -->
<div>...</div>

<!-- S Comment Text A -->
<div class="mod_a">
  ...
</div>
<!-- E Comment Text A -->

<!-- S Comment Text B -->
<div class="mod_b">
  ...
</div>
<!-- E Comment Text B -->
```

说明：合理的注释能够极大提升代码的可读性和降低维护成本。

---

## CSS 编写规范

### CSS 代码规范

【**强制**】**样式选择器，属性名，属性值关键字使用小写字母书写，属性字符串允许使用大小写，左括号不能另起一行；**

【**强制**】**每个属性声明末尾都要加分号；**

【**强制**】**Vue 单文件组件的样式块统一使用 `lang="scss" scoped`。禁止在模板中使用 `style` 行内样式；必须使用 `class` 绑定，将样式写入 scoped 样式表或公共样式文件中。**

- 说明：`scoped` 避免样式污染全局；`scss` 与项目预处理工具链保持一致。行内样式难以维护，无法复用主题变量，且容易导致样式冲突。动态计算值（如进度条宽度、拖拽位置）或第三方组件强制要求时可例外，但需在注释中说明原因。
- 正例：
  ```vue
  <template>
    <div class="page-title">标题</div>
  </template>

  <style lang="scss" scoped>
  .page-title { color: var(--te-color-primary); }
  </style>
  ```
- 反例：
  ```vue
  <div style="color: #409eff; font-size: 14px;">标题</div>
  ```

【**推荐**】**左括号与类名之间一个空格，冒号与属性值之间一个空格；**

【**推荐**】**以筛选器分组为维度进行分类；**

【**推荐**】**css 内属性排序参考如下示例：**

```css
.jdc {
  display: block;
  position: relative;
  float: left;
  width: 100px;
  height: 100px;
  margin: 0 10px;
  padding: 20px 0;
  font-family: Arial, 'Helvetica Neue', Helvetica, sans-serif;
  color: #333;
  background: rgba(0, 0, 0, 0.5);
  -webkit-border-radius: 10px;
  -moz-border-radius: 10px;
  -o-border-radius: 10px;
  -ms-border-radius: 10px;
  border-radius: 10px;
}
```

正例：

```css
.jdc {
  display: block;
  position: absolute;
}
```

反例：

```css
.JDC {
  DISPLAY: BLOCK;
  position: absolute
}
```

说明：写代码更简练，更优雅。

### 行首缩进

【**强制**】**缩进使用一个 tab，即 2 个空格，嵌套逐级缩进。**

说明：行首缩进距离应保持 html、js、ts、css 一致。

### 选择器

- 【**推荐**】**css 选择器中避免使用标签名和通用选择器。**

说明：从结构、表现、行为分离的原则来看，应该尽量避免 css 中出现 HTML 标签，并且在 css 选择器中出现标签名会存在潜在的问题，比如父组件影响子组件样式。

- 【**参考**】**使用子选择器。**

正例：

```css
.content > .title {
  font-size: 2rem;
}
```

反例：

```css
.content .title {
  font-size: 2rem;
}
```

说明：在进行非通用样式编写时，使用子选择器能够降低查询非子 DOM 元素带来的性能损耗。

### CSS 缩写

【**推荐**】**尽可能使用缩写去编写样式，而不是一个一个的罗列。**

正例：

```css
border-top: 0;
padding: 0 1em 2em;
```

反例：

```css
border-top-style: none;
padding-bottom: 2em;
padding-left: 1em;
padding-right: 1em;
padding-top: 0;
```

说明：精练的代码使人愉悦。

### CSS 书写格式

【**推荐**】**每一个属性单独占一行，子样式层级缩进。**

正例：

```css
button {
  width: 100px;
  height: 50px;
  color: #fff;
  background: #00a0e9;
}
```

反例：

```css
button {
  width: 100px; height: 50px; color: #fff; background: #00a0e9;
}
```

说明：我相信没有多少人会在 css 中把样式写在一行，这样写代码不优雅。

### 避免嵌套层级过多

【**推荐**】**将嵌套深度限制在 3 级。**

正例：

```scss
.main-title {
  .name {
    color: #ffffff;
  }
}
```

反例：

```scss
.main {
  .title {
    .name {
      .sur {
        color: #fffff;
      }
    }
  }
}
```

说明：大量的嵌套规范会影响可读性，除非业务逻辑十分复杂，否则可以考虑把嵌套的多级可以合并成一个。

### 引号的使用

【**强制**】**css 中需要引号的主要是属性值，这里统一使用单引号。**

正例：

```css
.jdc {
  font-family: 'Hiragino Sans GB';
}
```

反例：

```css
.jdc {
  font-family: "Hiragino Sans GB";
}
```

说明：统一标准，提升代码可读性的，不易给人产生歧义。

### CSS 注释

【**强制**】**注释内容第一个字符和最后一个字符都是一个空格字符，单独占一行，行与行之间相隔一行。**

正例：

```css
/* Comment Text */
.jdc {}
```

反例：

```css
.jdc {} /* Comment Text */
```

### CSS 样式名称

【**强制**】**必须使用组件库中已有的样式名称，详见组件库自带的样式清单文档。**

正例：

```css
.jdc {
  font-size: var(--te-font-size-b12);
}
```

反例：

```css
.jdc {
  font-size: 12px;
}
```

---

## 文字规范（Typography）

项目通过入口文件引入组件库样式（如 `main.ts` 引入 `dist/styles/index.css`），其 `:root` 中定义了权威的字体、字号、字重与行高变量。所有业务代码必须优先使用这些变量，禁止自行写死 `px` 或使用外部字体。

### 字体栈（font-family）规则

- 【**强制**】**页面默认字体统一使用 `var(--te-font-family)`，禁止重新声明整页 `font-family` 或私自引入外部字体文件 / CDN 字体。**

  说明：私自加载字体会增加网络请求、破坏离线环境一致性，并可能触发未知版权与加载失败问题。

  正例：

  ```scss
  .page-title {
    font-family: var(--te-font-family);
  }
  ```

  反例：

  ```scss
  /* 禁止引入外部字体或写死完整字体栈 */
  @import url('https://fonts.googleapis.com/...');

  .page-title {
    font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
  }
  ```

- 【**推荐**】**仅在数据大屏、仪表盘、数字展示等特殊场景，经设计确认后可使用 `var(--te-font-family-d-din)` 或 `var(--te-font-family-d-din-bold)`。**

  正例：

  ```scss
  .dashboard-value {
    font-family: var(--te-font-family-d-din-bold);
  }
  ```

- 【**强制**】**代码片段、日志、Monaco 编辑器等需要等宽显示的场景，使用系统默认等宽字体 `monospace`，禁止引入自定义等宽字体。**

  正例：

  ```scss
  .code-block {
    font-family: monospace;
  }
  ```

### 字号体系

`b*` 为正文（body），`h*` 为标题（heading），`oh*` 为超大展示标题（oversized heading）。建议优先使用下表中的 8 个主 token，避免使用语义不清或存在缺陷的别名。

| 变量 | 对应 px | 推荐使用场景 |
| --- | --- | --- |
| `var(--te-font-size-b12)` | 12px | 辅助说明、标签、时间戳、表格小标签、紧凑提示 |
| `var(--te-font-size-b14)` | 14px | 正文、表单输入、列表内容、按钮默认文字 |
| `var(--te-font-size-h16)` | 16px | 小标题、卡片标题、弹窗标题、强调性文本 |
| `var(--te-font-size-h20)` | 20px | 页面次级标题、模块标题 |
| `var(--te-font-size-h24)` | 24px | 页面主标题、大模块标题 |
| `var(--te-font-size-oh32)` | 32px | 数据大屏 / 仪表盘指标标题 |
| `var(--te-font-size-oh40)` | 40px | 数据大屏 / 仪表盘核心数字 |
| `var(--te-font-size-oh48)` | 48px | 数据大屏 / 仪表盘特大展示、空态图标 |

命名约定说明：

- 正文层级使用 `b12`、`b14`；
- 标题层级使用 `h16`、`h20`、`h24`；
- 超大展示标题仅用于大屏 / 驾驶舱场景，使用 `oh32`、`oh40`、`oh48`。

正例：

```scss
.title {
  font-size: var(--te-font-size-h24);
}

.label {
  font-size: var(--te-font-size-b12);
}
```

反例：

```scss
.title {
  font-size: 24px;
}

.label {
  font-size: 12px;
}
```

#### 字号别名使用注意

组件库同时提供了若干别名 token，但存在歧义或缺陷，建议按如下方式处理：

| 别名 | 实际映射 | 建议 |
| --- | --- | --- |
| `--te-font-size-base` | `--te-font-size-b14`（14px） | 可用，但新项目优先使用 `--te-font-size-b14` |
| `--te-font-size-small` | `--te-font-size-b14`（14px） | 语义不清，**不推荐**使用 |
| `--te-font-size-extra-small` | `--te-font-size-b12`（12px） | 可用，但优先使用 `--te-font-size-b12` |
| `--te-font-size-large` | `--te-font-size-h20`（20px） | 语义不清，**不推荐**使用 |
| `--te-font-size-extra-large` | `--te-font-size-h20`（20px） | 语义不清，**不推荐**使用 |
| `--te-font-size-medium` | `var(--te-font-size-b16)` | **不可用！`--te-font-size-b16` 未在组件库中定义，会导致样式失效** |

### 字重规则

- 【**推荐**】**普通正文、列表、表单等使用 `var(--te-font-weight-primary)`（400）。**

  正例：

  ```scss
  .form-item {
    font-weight: var(--te-font-weight-primary);
  }
  ```

- 【**推荐**】**选中态、导航高亮、表格表头、需要强调的标题等使用 `var(--te-font-weight-selected)`（700）。**

  正例：

  ```scss
  .nav-item.is-active {
    font-weight: var(--te-font-weight-selected);
  }
  ```

  反例：

  ```scss
  /* 禁止直接写死数值 */
  .nav-item.is-active {
    font-weight: 700;
  }
  ```

### 行高规则

- 【**推荐**】**多行正文、表单说明、列表描述等标准内容使用 `var(--te-font-line-height-primary)`（24px）。**

  正例：

  ```scss
  .description {
    font-size: var(--te-font-size-b14);
    line-height: var(--te-font-line-height-primary);
  }
  ```

- 【**推荐**】**标题类单行或短文本使用无单位倍数行高（如 `1.2`、`1.3`），超大展示标题可视设计需要降至 `1.1`。**

  正例：

  ```scss
  .section-title {
    font-size: var(--te-font-size-h20);
    line-height: 1.3;
  }

  .hero-number {
    font-size: var(--te-font-size-oh48);
    line-height: 1.1;
  }
  ```

- 【**推荐**】**图标类单行文字或纯装饰性数字可使用 `line-height: 1`，但需确认垂直居中无异常。**

### 强制要求

- 【**强制**】**字号必须使用组件库变量，禁止写死 `px`。**

  说明：写死 `px` 会导致后续主题升级、大屏适配、设计走查返工困难。

  正例：

  ```scss
  font-size: var(--te-font-size-b14);
  ```

  反例：

  ```scss
  font-size: 14px;
  ```

- 【**强制**】**禁止私自引入外部字体文件或从 CDN 加载字体。**

  说明：所有字体栈应由组件库统一维护，避免版权、加载失败及离线环境问题。

- 【**强制**】**禁止使用 `--te-font-size-medium`。**

  说明：该变量引用未定义的 `--te-font-size-b16`，在浏览器中不会生效，属于组件库已知缺陷。

- 【**推荐**】**设计稿中出现 18px 等无精确 token 的字号时，与 UI 设计师对齐，优先就近取 `h16`（16px）或 `h20`（20px），禁止直接写 `18px`。**

### 例外条款

以下场景允许不完全受上述字号变量约束，但仍需遵循本规范精神：

- **图标文字**：图标字体（iconfont）或组件库图标组件提供的字形，其“尺寸”本质是图标大小，应优先使用组件库图标组件的 `size` 属性；若需配合文字，文字部分仍使用字号 token。
- **等宽代码**：代码、日志、密钥串等显示使用 `font-family: monospace`，字号仍建议使用 `b12` / `b14`。
- **SVG 资源**：SVG 内部文字属于图形资源，由设计工具导出，不受 CSS 文字变量约束；但业务代码中覆盖在 SVG 上的文本标签仍应使用字号 token。
- **组件库内部 13px 辅助文字**：`ts-web-package` 内部在 badge、alert、collapse 等组件使用了 13px，这是组件库内部实现细节，业务代码不应复制。业务中若设计稿确实需要 13px，优先改为 `b12` 或 `b14`；必须保留时，请在注释中说明 UI 确认情况，且不得作为通用做法推广。

> 注意：`13px` 在组件库中无对应 token，因此在业务代码中属于“无精确 token”的字号，原则上不允许写死。

---

## JavaScript/Typescript 规范

### 属性与函数名称规范

- 【**建议**】**采用小写驼峰命名 lowerCamelCase（构造函数除外），代码中的命名均不建议以下划线，也不建议以下划线或美元符号结束；**
- 【**强制**】**方法名、参数名、成员变量、局部变量都统一使用 lowerCamelCase 风格，遵从驼峰形式。**

正例：

```text
localValue / getHttpMessage() / inputUserId
```

反例：

```text
Name / name_ / name$
```

- 【**推荐**】**其中 method 方法命名必须是动词或者动词 + 名词形式。**

正例：

```text
saveShopCarData / openShopCarInfoDialog
```

反例：

```text
save / open / show / go
```

- 【**强制**】**全局常量采用全部大写，单词间用下划线隔开。**

正例：

```text
MAX_STOCK_COUNT
```

反例：

```text
Max_Count
```

说明：全大写是为了把全局常量与常量和变量区分开，力求语义表达完整清楚。

- 【**强制**】**局部常量命名采用小写驼峰命名。**

正例：

```text
maxCount
```

反例：

```text
Max_Count
```

- 【**强制**】**声明 interface、枚举、type 使用大驼峰，其中枚举成员全部大写，单词间用下划线隔开。**

正例：

```ts
interface MaxCount {}
type MaxCount = {}
enum MaxCount = { TEST: xxx }
```

反例：

```ts
maxCount
Max_Count
```

- 【**强制**】**构造函数首字母大写。**

正例：

```js
var fooItem = new Foo();
```

反例：

```js
var fooItem = new foo();
```

说明：首字母大写为了将构造函数与普通函数区分开。

- 【**参考**】**单个函数行数不建议超过 100 行。**

说明：成为顶级开发人员的必备素养之一。

### 函数操作

- 【**强制**】**编写函数时标准格式，函数名或 function 关键字与左括号之间允许有空白。命名函数要求函数名和 function 关键字之间有空格，但是匿名函数要求不加空格，同时代码中操作符前后都需要添加空格。**

正例：

```js
function func (x) {
  let sum = 1 + 2;
}
```

反例：

```js
function func(x){
  let sum = 1+2;
}
```

说明：善用空格能够使代码看着更加舒适，就好像我们写文档时调整字间距一样。

- 【**强制**】**使用函数声明，而不是函数表达式。**

正例：

```js
function foo () {
  // do something
}
```

反例：

```js
const foo = function () {
  // do something
}
```

- 【**推荐**】**在业务场景允许的情况下，尽量使用箭头函数。**

正例：

```js
let test;
if (isUse) {
  test = () => {
    // do something
  }
}
```

反例：

```js
if (isUse) {
  function test () {
    // do something
  }
}
```

### 缩进

- 【**强制**】**使用 2 个空格进行缩进。**

正例：

```js
if (x < y) {
  x += 10;
} else {
  x += 1;
}
```

- 【**推荐**】**不同逻辑、不同语义、不同业务的代码之间插入一个空行分隔开来以提升可读性。**

说明：从空间上把实现逻辑不关联的代码段分割开，任何情形，同时也没有必要插入多个空行进行隔开。

### 大括号

【**强制**】**大括号采用 One True Brace Style 风格。**

正例：

```js
if (foo) {
  bar();
} else {
  baz();
}
```

说明：虽然可选的风格有很多，但这里推荐 One True Brace Style。

### 数组操作

【**推荐**】**建议使用字面量值创建数组，使用 push 向数组中添加元素，使用拓展运算符复制数组，活用解构赋值。**

正例：

```js
const items = [];
items.push('str');
const itemsCopy = [...items];
const [first] = arr;
```

反例：

```js
const items = new Array();
items[1] = 'str';
for (i = 0; i < len; i++) {
  itemsCopy[i] = items[i];
}
const first = arr[0];
```

说明：字面量创建能够省略复杂的传参初始化过程，而它相比于 new 创建对象的缺点几乎可以忽略不计。

### 字符串操作

- 【**推荐**】**统一使用单引号，不使用双引号(")。**

正例：

```js
let str = 'foo';
let testDiv = '<div id="test"></div>';
```

反例：

```js
let str = "foo";
let testDiv = "<div id='test'></div>";
```

说明：统一标准。

- 【**推荐**】**程序化生成字符串时，使用模板字符串。**

正例：

```js
const test = 'iamstring';
const str = `ab${test}`;
```

反例：

```js
const test = 'iamstring';
let str = 'ab' + test;
```

说明：模板字符串的优点很多，可拼接、可插值、可运算、可嵌套，用过的人都知道。

### 对象操作

【**推荐**】**使用字面量来代替对象构造器。**

正例：

```js
var user = {
  age: 0,
  name: 1,
  city: 3,
};
```

反例：

```js
var user = new Object();
user.age = 0;
user.name = 0;
user.city = 0;
```

说明：字面量创建的好处在数组相关说明中已提及，这里不再赘述。

### Var/let/const 用法

【**强制**】**支持 ES6 的情况下，const 用在那些永远不会改变的变量申明，let 申明其他变量，不应当使用 var。**

正例：

```js
const oneDayHours = 24;
let currentTime = '00:00';
```

反例：

```js
var oneDayHours = 24;
let currentTime = '00:00';
```

### 省略写法

【**推荐**】**不建议使用省略写法。**

正例：

```js
if (desk) {
  desk.init();
}
```

反例：

```js
if (desk) desk.init();
```

说明：方便了自己，为难了他人（或未来的自己）。

### this 的转换命名

【**强制**】**对上下文 this 的引用只能有一种，是使用 self or th or _th 可以根据当前项目中哪个用的多选择。**

说明：我们都不希望一段代码中同时存在 self/th/_th 等多个指向同一个实例的变量引用，这太 low 了。

### let、const、var

【**强制**】**如无必要，不使用 var，建议使用 let 或 const。**

说明：var 属于函数级作用域，穿透能力十分强大，易产生变量污染。

### 判断条件

【**推荐**】**建议优先使用 === 和 !== 而不是 == 和 !=。**

### 注释写法

- 【**强制**】**单行注释使用 `//` 在代码上面写注释；**
- 【**强制**】**多行注释可使用 `/* */`，也就是以 `/` 开始，`*/` 结束，中间是注释内容；**
- 【**强制**】**推荐每个 Javascript/TypeScript/Html 都在开头写一段注释内容，一段不超过 30 字的描述即可。**

正例：

- 对外提供的函数注释建议格式如下：

```js
/* 方法说明
 * @method 方法名
 * @description 方法描述
 * @param {参数类型} 参数名 参数说明
 * @return {返回值类型} 返回值说明
 */
```

- 非对外提供的函数注释建议格式如下：

```js
/* 方法说明
 * @method 方法名
 * @description 方法描述
 * @param {参数类型} 参数名 参数说明
 */
```

- 模块注释建议格式如下：

```js
/* 模块说明
 * @module 模块名
 * @description 模块描述
 */
```

- 组件注释建议格式如下：

```js
/* 组件说明
 * @component 组件名
 * @description 组件描述
 */
```

- 类注释建议格式如下：

```js
/* 类说明
 * @class 类名
 * @description 类描述
 * @constructor 构造函数
 */
```

---

## 代码编写建议

## 推荐编写习惯

### ES6,7

【**推荐**】**优先使用 ES6,7 中新增的语法和函数，提升代码品质。**

### 特殊字符转义

【**推荐**】**针对特殊符号，使用转义符拼写。**

正例：

```html
<a href="#">more&gt;&gt;</a>
```

反例：

```html
<a href="#">more>></a>
```

说明：在 HTML 中不能使用小于号 `<` 和大于号 `>` 等特殊字符，浏览器会将它们作为标签解析，若要正确显示，在 HTML 源代码中使用字符实体。

## 不推荐编写习惯

### instanceof

**不建议使用 instanceof 判断对象类。**

说明：instanceof 会顺着原型链往上找类名，这类问题一旦发生很难定位，有其他可替代的方案，因此不建议使用。

### Eval

**不建议使用 eval。**

说明：在老版本浏览器中性能非常低（甚至下降超过十倍）、容易产生内存泄漏，而且无法 debug 问题难以定位。

### for-in

**不建议使用 for-in，数组操作推荐 foreach、some、every、对象属性操作推荐 Object.key。**

说明：使用 forin 对数组进行操作，顺序是不固定的，虽然它和 for 长得很像。

### undefined 判断

**不建议使用 undefined 进行变量判断；使用 typeof 和字符串 'undefined' 对变量进行判断。**

正例：

```js
if (typeof person === 'undefined') {
}
```

反例：

```js
if (person === undefined) {
}
```

### console.log

**console.log 大量使用会有性能问题，因此仅建议在调试环境使用。**

### 链式赋值

**不建议使用链式赋值。**

正例：

```js
var a = 1;
var b = 1;
var c = 1;
```

反例：

```js
var a = b = c = 1;
```

### 同行声明

**不建议一行同时声明多个变量。**

正例：

```js
var a;
var b;
var c;
```

反例：

```js
var a, b, c;
```

---

## 安全编码规范

### 弱口令漏洞

**问题说明：**

弱口令（weak password）没有严格和准确的定义，通常认为容易被别人猜测到或被破解工具破解的口令均为弱口令。

**正例：**

```text
AbcD@12457#$
```

**反例：**

1. 123456
2. Abcdefh
3. tzf.tzf

**整改建议：**

编码过程中，设置密码规则应当包涵盖以下原则：

1. 禁止设置空口令或系统缺省的口令；
2. 口令长度不小于 8 个字符；
3. 口令不应该为连续的某个字符（例如：AAAAAAAA）或重复某些字符的组合（例如：tzf.tzf.）；
4. 口令应该为以下四类字符的组合，大写字母(A-Z)、小写字母(a-z)、数字(0-9)和特殊字符。每类字符至少包含一个。如果某类字符只包含一个，那么该字符不应为首字符或尾字符。
5. 至少若干天内更换一次口令，防止未被发现的入侵者继续使用该口令。

### SQL 注入漏洞

**问题说明：**

SQL 注入攻击（SQL Injection），简称注入攻击、SQL 注入，被广泛用于非法获取网站控制权，是发生在应用程序的数据库层上的安全漏洞。在设计程序，忽略了对输入字符串中夹带的 SQL 指令的检查，被数据库误认为是正常的 SQL 指令而运行，从而使数据库受到攻击，可能导致数据被窃取、更改、删除，以及进一步导致网站被嵌入恶意代码、被植入后门程序等危害。

**正例：**

攻击者通过前端进行 SQL 注入步骤：

1. 通过前端表单或输入域名以及页面请求页面将 `and 0<>(select count(*) from *)`、`and 0<>(select count(*) from admin)`、`and 1=(select count(*) from admin where len(*)>0)--` 等字符串输入；
2. 前端在提交给后台之前通过 4.2 章节安全策略的正则表达式匹配发现异常，数据不予提交。

**反例：**

攻击者通过前端进行 SQL 注入步骤：

1. 通过前端表单或输入域名以及页面请求页面将 `and 0<>(select count(*) from *)`、`and 0<>(select count(*) from admin)`、`and 1=(select count(*) from admin where len(*)>0)--` 等字符串输入；
2. 前端未做任何校验或转义处理，通过表单发送至服务器；
3. 步骤 2 中恶意 SQL 命令将抵达服务器并执行；
4. 攻击者根据报错信息攻击者能够猜出表名，通过获取的信息筹划进行下一步攻击。

**安全策略：**

编码过程中，在用户的输入向后台提交前，应通过正则表达式，或限制长度，针对敏感或必须提交的信息，应当对诸如 select 等关键字进行转义或置换。

### XSS 漏洞

**问题说明：**

XSS 攻击使用到的技术主要为 HTML 和 Javascript，也包括 VBScript 和 ActionScript 等。XSS 攻击对 WEB 服务器虽无直接危害，但是它借助网站进行传播，使网站的使用用户受到攻击，导致网站用户帐号被窃取，从而对网站也产生了较严重的危害。

XSS 攻击分为：非持久型 XSS、持久型 XSS、基于字符集 XSS、基于 FLASH 的 XSS、未经验证跳转 XSS。

注：Angular 框架默认会启动对 XSS 防御。

**反例：**

1. 为实现关键字搜索功能编写如下代码段；
2. 攻击者将网站链接添加恶意代码后进行散播；
3. 当浏览器请求时，服务端会解析出请求参数 keyword，得到 `<script>alert('XSS');</script>`，拼接到 HTML 中返回给浏览器。形成了如下的 HTML，恶意代码在客户端执行，至此 XSS 攻击完成。

**正例：**

1. 为实现关键字搜索功能编写如下代码段；
2. 攻击者将网站链接添加恶意代码后进行散播；
3. 当浏览器请求时，服务端会解析出请求参数 keyword，得到 `<script>alert('XSS');</script>`，拼接到 HTML 中返回给浏览器。形成了如下的 HTML，恶意代码被转义，无法执行。

**安全策略：**

1. 使用 xss.js 开源 XSS Filter 库或编写校验方法进行表单校验；
2. 前端项目负责人应当负责启动 CSP 防护，针对不同业务场景对 CSP 限制类型进行约束；
3. 前端项目负责人应当结合项目场景，酌情设置 HttpOnly Cookie，此项措施是预防 XSS 攻击窃取用户 cookie 最有效的防御手段，有效避免网页的 cookie 被客户端恶意 JavaScript 窃取，保护用户 cookie 信息。
4. 编码过程中，针对基于字符集的 XSS 漏洞，应当指定 `<meta charset="utf-8">`，同时 XML 中标签必须闭合。
5. 编码过程中，针对基于 FLASH 的 XSS 漏洞，对 Flash 接受用户输入的参数进行过滤转义处理。

### jQuery 跨站脚本漏洞

**问题描述：**

jQuery 1.6.3 之前版本中存在跨站脚本漏洞。当使用 location.hash 选择元素时，通过特制的标签，远程攻击者利用该漏洞注入任意 web 脚本或 HTML。

**安全策略：**

前端项目负责人应当检查当前项目使用 jQuery 版本，如低于 1.6.3，应当升级最新版 jQuery，同时开发人员配合修改 jQuery 低版本引用，如轮播图，tab 切换等。

### 信息泄露漏洞

**问题描述：**

如身份证，电话号码，邮箱等信息泄露，通常是页面中或数据中存在此类信息，在征求客户同意的情况下，可以忽略。

**安全策略：**

编码过程中，如非业务需求，应当避免敏感信息在页面上直接进行展示，只有通过需要二次操作或身份认证的方式才能查看。

### CSRF 漏洞

**问题描述：**

CSRF（Cross-site request forgery），中文名称：跨站请求伪造，可以理解为 CSRF 攻击：攻击者盗用了当前用户的身份，在用户不知情的情况下以用户的名义发送恶意请求。

CSRF 攻击是源于 WEB 的隐式身份验证机制，WEB 的身份验证机制虽然可以保证一个请求是来自于某个用户的浏览器，但却无法保证该请求是用户批准发送的。

**安全策略：**

前端项目负责人应当结合项目具体要求进行如下 CSRF 防护：

1. 在请求地址中添加 token 并验证；
2. 在前端工程请求管理服务中添加全局验证 HTTP Referer 字段，根据域名判断是否合法。

### 文件上传漏洞

**问题描述：**

文件上传漏洞是指用户上传了一个可执行的脚本文件，并通过此脚本文件获得了执行服务器端命令的能力。常见场景是 web 服务器允许用户上传图片或者普通文本文件保存，而用户绕过上传机制上传恶意代码并执行从而控制服务器。需要说明的是上传文件操作本身是没有问题的，问题在于文件上传到服务器后，服务器怎么处理和解释文件。

**正例：**

通过下述代码判断文件后缀名，如与预期不符则不给与提交。

**安全策略：**

编码过程中，涉及文件上传时应进行用户身份校验，在进行文件上传时，应当对用户的身份进行合法性校验和格式校验；

编码过程中，应对上传文件的后缀进行校验判断是否合法。

### 点击劫持漏洞

**问题描述：**

点击劫持是一种视觉欺骗的攻击手段。攻击者将需要攻击的网站通过 iframe 嵌套的方式嵌入自己的网页中，并将 iframe 设置为透明，在页面中透出一个按钮诱导用户点击，X-Frame-Options HTTP 响应头是用来给浏览器指示允许一个页面可否在 `<frame>`, `<iframe>` 或者 `<object>` 中展现的标记。网站可以使用此功能，来确保自己网站的内容没有被嵌到别人的网站中去，也从而避免了点击劫持（clickjacking）的攻击。

X-Frame-Options 有三个值：DENY、SAMEORIGIN、ALLOW-FROM，分别表示：页面不允许在 frame 中展示，即便是在相同域名的页面中嵌套也不允许；该页面可以在相同域名页面的 frame 中展示；该页面可以在指定来源的 frame 中展示。

**安全策略：**

前端负责人应给所有页面的拦截器中结合业务场景增加 X-Frame-Options HTTP 头设置：

- DENY：拒绝任何域加载；
- SAMEORIGIN：允许同源域下加载；
- ALLOW-FROM：可以定义允许 frame 加载的页面地址。

编码过程中：增加 js 代码层面逻辑防护。

### Web 服务最小权限配置

**问题描述：**

服务器默认启用了所有请求方法，其中包括较危险的方法，如 TRACE。如果工作中没有用到这些方法，可设置禁用，同时禁止 HTTP TRACE 请求能够防御 HTTP 报头追踪漏洞。

**正例：**

配置文件中仅配置了前后端交互需要的方式：GET、POST、HEAD、OPTIONS。

**反例：**

默认开启所有请求方式。

**安全策略：**

前端负责人根据业务场景需求限制允许的 HTTP 请求类型。

### 敏感信息自动填充

**问题描述：**

网页禁用表单的自动完成功能是由 input 元素的 autocomplete 属性控制，关闭表单的自动完成就是设置 autocomplete="off"，默认是开的值为 on。

**正例：**

```html
<input type="password" name="password" placeholder="请输入密码" autocomplete="off">
```

**反例：**

```html
<input type="password" name="password" placeholder="请输入密码" autocomplete="on">
```

**安全策略：**

编码过程中，输入敏感数据的输入框应当设置 autocomplete="off" 属性。

### 认证信息明文传输

**问题描述：**

以 https 方式将用户认证信息提交给后台 web 服务器。

**安全策略：**

编码过程中，用户凭据必须经过加密且以 POST 方式提交，使用 HTTPS 协议来加密通道、认证服务。

编码过程中，在应用程序进行身份验证时，应当持续使用 HTTPS 连接，认证站点使用 HTTPS 协议。

### 身份验证策略

**问题描述：**

身份认证策略是保证用户信息安全的第一道防线，需要多种策略结合进行防护。

**安全策略：**

编码过程中，用户凭据应经过加密且以 POST 方式提交，使用 HTTPS 协议来加密通道、认证服务；

编码过程中，身份认证失败时，应当使用"用户名或密码错误"来提示失败；

编码过程中，登录入口增加防止暴力或撞库猜解（利用已泄露的密码字典进行批量登录尝试）的措施，超过一定次数验证失败自动启用用户真人验证，超过多次验证失败自动启用账户锁定机制限制其访问。

### 输入验证策略

**问题描述：**

任何来自用户输入的数据，如 URL 和参数、HTTP 头部、Javascript 或其他嵌入代码提交的信息，都属于不可信数据。在应用外部边界或内部每个组件或功能边界，都将其当做潜在的恶意输入来校验。

**安全策略：**

前端负责人应当结合业务场景，制定全局输入黑名单，针对用户输入的数据进行校验，对于不可信数据中包含不良输入字符时，应当设置拦截器，阻止该数据发送至服务端。

编码过程中，应当对用户的访问页面进行控制，在进行关键操作时应当确认所提交的内容是否与用户的身份匹配，避免越权访问。

---

## 代码 lint 校验规范（内置在脚手架模版工程里面）

### eslint

项目级配置：官方默认的规范配合 vscode 插件（prettier+eslint），自定义部分校验规范。

### stylelint

项目级配置：官方默认的规范配合 vscode 插件（stylelint）。

### commitlint

git 提交规范严格按照如下提交规范：

```text
fix|feat|update: 提交日志
示例: fix: 修复xxxbug
```

---

## vue3 开发范式规范

- 【**强制**】**Vue 单文件组件必须使用组合式 API 与 `<script setup lang="ts">` 语法糖。**
  - 说明：`setup` 语法糖减少了样板代码，配合 TypeScript 可提供更好的类型推导。
  - 正例：
    ```vue
    <script setup lang="ts">
    defineOptions({
      name: 'HomePage',
    });
    </script>
    ```
  - 反例：
    ```vue
    <script lang="ts">
    export default defineComponent({ ... });
    </script>
    ```

- 【**强制**】**每个 Vue 组件必须显式声明 `name`，且使用大驼峰（PascalCase）。**
  - 正例：`defineOptions({ name: 'UserListPage' })`。
  - 反例：`name: 'user-list-page'`、`name: 'userListPage'`。

- 【**强制**】**组件样式块统一使用 `<style lang="scss" scoped>`。**
  - 说明：`scoped` 限制样式只作用于当前组件，避免全局污染；`scss` 与项目预处理工具链保持一致。
  - 正例：
    ```vue
    <style lang="scss" scoped>
    .user-list-page { ... }
    </style>
    ```
  - 反例：
    ```vue
    <style>
    .user-list-page { ... }
    </style>
    ```

- 【**推荐**】**优先使用 `ref`/`reactive`/`computed` 等响应式 API，避免混用选项式 API。**

---

## Vue 社区最佳实践参考（AI 生成代码时可参考）

> 以下规范来自社区 `frontend-standard-skills/vuejs`，用于补充 Vue 3 工程实践。**如与上述规范冲突，以上述规范为准。**

### 组件与响应式

- **路由组件懒加载**：业务路由组件必须使用动态导入，禁止在路由文件顶层静态 import。
  ```ts
  { path: '/agent', component: () => import('@/views/agent/AgentList.vue') }
  ```
- **Props / Emits 必须带泛型类型**：优先使用 `defineProps<T>()` / `defineEmits<T>()`，禁止 runtime-only 的 `defineProps({ ... })` 与 `any`。
- **禁止直接修改 Props**：子组件需要修改父组件数据时，必须通过 `emit` 事件通知父组件。
- **响应式选择**：
  - 基础类型或可能被整体替换的值使用 `ref`；
  - 不会整体重新赋值的内聚对象可使用 `reactive`；
  - API 返回的大列表、图表数据集等只读/少变数据优先使用 `shallowRef` / `shallowReactive`。
- **推导优于 watch**：能用 `computed` 解决的派生状态，禁止写 `ref + watch` 手动同步。

### 状态管理

- **Pinia 按领域拆分 store**：如 `useUserStore`、`useCartStore`，禁止一个 store 包揽全应用状态。
- **优先使用 setup-style store**（`ref`/`computed`/函数返回），与 `<script setup>` 保持一致。
- 仅当状态需要跨 3+ 层组件或在组件树外存活时才放入 Pinia，局部状态留在组件内。

### 可访问性（WCAG 2.1 AA）

- 使用真实语义标签（`<button>`、`<nav>`、`<main>`、`<label for>`），禁止 `<div @click>` 模拟按钮。
- 图片必须提供上下文相关的 `alt`，装饰性图片使用 `alt=""`；必须给出明确 `width`/`height`。
- 弹窗/抽屉需实现 `role="dialog"` + `aria-modal` + 焦点捕获 + `Escape` 关闭 + 关闭后焦点回 trigger；优先使用组件库封装好的组件。
- `v-html` 仅用于经安全过滤的内容，禁止用于按钮、链接等可交互 markup。

### 性能（Core Web Vitals）

- **图片策略**：首屏 LCP 图片不设置 `loading="lazy"`，可使用 `fetchpriority="high"`；非首屏图片使用 `loading="lazy"` + 明确尺寸。
- **异步加载重型组件**：编辑器、图表、大弹窗等使用 `defineAsyncComponent` + `<Suspense>`，避免进入首屏 bundle。
- **`v-memo` 谨慎使用**：仅在 Vue DevTools 确认存在重复渲染开销后再对昂贵 `v-for` 列表使用。
- **性能测量以生产构建为准**：使用 `vite build && vite preview` 测量，禁止以 `vite dev` 结果作为性能依据。

### UX 一致性

- 每个含异步数据的视图必须提供 loading、empty、error 三种状态；loading 骨架屏应近似最终布局尺寸，避免切换时产生 CLS。
- 移动端优先，交互元素触摸目标不小于 44×44px，操作后 100ms 内给出反馈。

### 工具与 CI（补充）

- 保持 `eslint-plugin-vue` 与 `eslint-plugin-vuejs-accessibility` 开启，并将可访问性规则设为构建阻断。
- 关键路径建议补充 Playwright + `@axe-core/playwright` 的可访问性 e2e 测试。
- 图片/字体/数据获取变更时，使用 Lighthouse CI 在真实构建产物上校验 LCP/CLS/INP。

---

## RCS 代码评审标准规范

严格按照以上标准评审执行。

---

## npm 包管理规范

### node 版本

18.x 版本及以上。项目 `package.json` 中 `engines.node` 应保持与此一致。

### npm 包使用

1. 同类型的包不允许引用多个，比如 ajax 和 axios；
2. 区分开发依赖和生产依赖，比如构建工具 webpack 或 vite 用开发依赖；
3. 优先使用已有私有 npm 仓库中的包，避免重复造轮子；

### npm 包私有仓库发布

1. npm 包开发需有 readme.md 文件详细描述，经评审后方可发布；
2. npm 包开发需有严格的自测，迭代版本需向上兼容；
3. npm 包开发需用 ts 语法开发，并导出对应的类型声明文件；
4. npm 包版本升级需遵守 语义化版本控制规范。

---

## 组件开发规范

1. 组件需求来源明确，并有相应的开发设计输出，严格按照组件发布流程执行；
2. 组件充分自测后方可提 MR 走发布上线流程。

### 组件名称命名

【**强制**】**命名规则为每个组件定义其名称，其命名规则为大驼峰方式。**

正例：

```vue
<script lang="ts" setup>
defineOptions({
  name: 'MyComponent',
});
</script>
```

或

```vue
<script lang="ts">
export default defineComponent({
  name: 'MyComponent',
});
</script>
```

反例：

```vue
<script lang="ts" setup>
defineOptions({
  name: 'my-component',
});
</script>
```

```vue
<script lang="ts">
export default defineComponent({
  name: 'my-component',
});
</script>
```

```vue
<script lang="ts" setup>
defineOptions({
  name: 'myComponent',
});
</script>
```

```vue
<script lang="ts">
export default defineComponent({
  name: 'myComponent',
});
</script>
```
