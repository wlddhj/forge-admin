import request from '@/utils/request'

export interface UpdateUserInfoRequest {
  nickname?: string
  phone?: string
  email?: string
}

export interface UpdatePasswordRequest {
  oldPassword: string
  newPassword: string
}

export interface UpdateAvatarRequest {
  avatar: string
}

export function updateUserInfo(data: UpdateUserInfoRequest) {
  return request.put('/system/user/profile', data)
}

export function updatePassword(data: UpdatePasswordRequest) {
  return request.put('/system/user/password', data)
}

export function updateAvatar(data: UpdateAvatarRequest) {
  return request.put('/system/user/avatar', data)
}

export function getUserInfo() {
  return request.get('/system/user/profile').then(res => res.data)
}
