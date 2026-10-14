import axios from 'axios'
import { ElLoading, ElMessage } from 'element-plus'
import { saveAs } from 'file-saver'
import { getToken } from '@/utils/auth'
import errorCode from '@/utils/errorCode'
import { blobValidate } from '@/utils/sgj'

const baseURL = import.meta.env.VITE_APP_BASE_API
let downloadLoadingInstance: ReturnType<typeof ElLoading.service>

export default {
  name(name: string, isDelete = true) {
    const url = baseURL + "/common/download?fileName=" + encodeURIComponent(name) + "&delete=" + isDelete
    axios({
      method: 'get',
      url: url,
      responseType: 'blob',
      headers: { 'Authorization': 'Bearer ' + getToken() }
    }).then((res: any) => {
      const isBlob = blobValidate(res.data)
      if (isBlob) {
        const blob = new Blob([res.data])
        this.saveAs(blob, decodeURIComponent(res.headers['download-filename']))
      } else {
        this.printErrMsg(res.data)
      }
    })
  },
  resource(resource: string) {
    const url = baseURL + "/common/download/resource?resource=" + encodeURIComponent(resource)
    axios({
      method: 'get',
      url: url,
      responseType: 'blob',
      headers: { 'Authorization': 'Bearer ' + getToken() }
    }).then((res: any) => {
      const isBlob = blobValidate(res.data)
      if (isBlob) {
        const blob = new Blob([res.data])
        this.saveAs(blob, decodeURIComponent(res.headers['download-filename']))
      } else {
        this.printErrMsg(res.data)
      }
    })
  },
  zip(url: string, name: string) {
    const downloadUrl = baseURL + url
    downloadLoadingInstance = ElLoading.service({ text: "正在下载数据，请稍候" })
    axios({
      method: 'get',
      url: downloadUrl,
      responseType: 'blob',
      headers: { 'Authorization': 'Bearer ' + getToken() }
    }).then((res: any) => {
      const isBlob = blobValidate(res.data)
      if (isBlob) {
        const blob = new Blob([res.data], { type: 'application/zip' })
        this.saveAs(blob, name)
      } else {
        this.printErrMsg(res.data)
      }
      downloadLoadingInstance.close()
    }).catch((r: any) => {
      console.error(r)
      ElMessage.error('下载文件出现错误，请联系管理员！')
      downloadLoadingInstance.close()
    })
  },
  /**
   * 下载服务端现场生成的文件（笔记导出走这里）。
   *
   * 与 resource() 的区别：不预设 mime、不弹 loading——导出很快，弹个 loading 只会闪一下。
   * 文件名取自响应头 download-filename（后端已做百分号编码），decode 回来就是保存对话框里的中文名
   *
   * @param url 相对地址（会自动拼 baseURL），如 noteExportUrl(noteId)
   */
  file(url: string) {
    axios({
      method: 'get',
      url: baseURL + url,
      responseType: 'blob',
      headers: { 'Authorization': 'Bearer ' + getToken() }
    }).then((res: any) => {
      const isBlob = blobValidate(res.data)
      if (isBlob) {
        const blob = new Blob([res.data])
        this.saveAs(blob, decodeURIComponent(res.headers['download-filename']))
      } else {
        this.printErrMsg(res.data)
      }
    }).catch((r: any) => {
      console.error(r)
      ElMessage.error('导出失败，请稍后重试')
    })
  },
  saveAs(text: Blob, name: string, opts?: any) {
    saveAs(text, name, opts)
  },
  async printErrMsg(data: any) {
    // 响应体不一定是 JSON（网关报错页、代理错误等），解析失败时不能反过来把用户晾在未处理的 rejection 上
    try {
      const resText = await data.text()
      const rspObj = JSON.parse(resText)
      const errMsg = errorCode[rspObj.code] || rspObj.msg || errorCode['default']
      ElMessage.error(errMsg)
    } catch {
      ElMessage.error(errorCode['default'])
    }
  }
}
