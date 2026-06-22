import JSEncrypt from 'jsencrypt'
import request from '@/api'

/**
 * RSA 加密工具模块
 * - 从后端获取公钥
 * - 使用 RSA 公钥加密密码
 */

/** 从后端获取 RSA 公钥（Base64编码） */
export async function fetchRsaPublicKey(): Promise<string> {
  const res = await request.get<{ code: number; message: string; data: { publicKey: string } }>(
    '/front/auth/rsa-public-key'
  )
  return res.data.data.publicKey
}

/**
 * 使用 RSA 公钥加密明文密码
 * @param password 明文密码
 * @param publicKeyBase64 Base64编码的RSA公钥
 * @returns Base64编码的密文
 */
export function encryptPassword(password: string, publicKeyBase64: string): string {
  const encryptor = new JSEncrypt()
  encryptor.setPublicKey(publicKeyBase64)
  const encrypted = encryptor.encrypt(password)
  if (!encrypted) {
    throw new Error('密码加密失败，请刷新页面重试')
  }
  return encrypted
}
