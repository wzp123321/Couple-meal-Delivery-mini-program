// 头像上传（前端-00-基础设施.md §4.2 / 10-关系绑定/后端.md §3.6）。
// 两步走的「第一步」：chooseAvatar 拿到本地临时路径 → uni.uploadFile 到 /api/user/avatar 换相对路径。
// 不走 request.ts（uploadFile 是独立链路），但同样注入 Authorization（见 §4.2）。
// 注意：res.data 是字符串，必须 JSON.parse 后判 code（§4.2 提示）。
import { getToken } from './auth'

export function uploadAvatar(tempFilePath: string): Promise<string> {
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: import.meta.env.VITE_API_BASE + '/api/user/avatar',
      filePath: tempFilePath,
      name: 'file',
      header: { Authorization: 'Bearer ' + getToken() },
      success: (res) => {
        try {
          const body = JSON.parse(res.data) as {
            code: number
            msg?: string
            data?: { avatar: string }
          }
          if (body.code === 0 && body.data?.avatar) {
            resolve(body.data.avatar) // 相对路径
          } else {
            reject(new Error(body.msg || '头像上传失败'))
          }
        } catch (e) {
          reject(e)
        }
      },
      fail: (err) => reject(err),
    })
  })
}
