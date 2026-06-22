import request from '@/api'

export interface SignatureRecordVO {
  id: number
  documentId: number
  signerId: number | null
  signerName: string
  signTime: string
  sealImageUrl: string | null
  signatureHash: string
  createTime: string
}

export function signDocument(data: { documentId: number; signerName: string; sealImageUrl?: string | null }) {
  return request.post<{ code: number; message: string; data: SignatureRecordVO }>('/front/signatures', data)
}

export function getDocumentSignatures(documentId: number) {
  return request.get<{ code: number; message: string; data: SignatureRecordVO[] }>(`/front/signatures/document/${documentId}`)
}

export function getMySignatures() {
  return request.get<{ code: number; message: string; data: SignatureRecordVO[] }>('/front/signatures/mine')
}

export function verifySignature(data: { documentId: number; signatureHash: string }) {
  return request.post<{ code: number; message: string; data: { documentId: number; valid: boolean } }>('/front/signatures/verify', data)
}
