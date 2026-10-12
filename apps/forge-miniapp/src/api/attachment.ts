import { API_BASE_URL } from '@/config'

export const attachmentApi = {
  uploadAvatar: (filePath: string): Promise<{ url: string }> => {
    const accessToken = uni.getStorageSync('accessToken')
    return new Promise((resolve, reject) => {
      uni.uploadFile({
        url: API_BASE_URL + '/attachment/upload',
        filePath,
        name: 'file',
        formData: { bizType: 'APP_AVATAR' },
        header: { Authorization: 'Bearer ' + accessToken },
        success: (res) => {
          const body = JSON.parse(res.data)
          if (body.code === 200) {
            resolve(body.data)
          } else {
            reject(new Error(body.message))
          }
        },
        fail: reject
      })
    })
  }
}