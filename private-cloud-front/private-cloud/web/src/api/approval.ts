import request from '@/api'

export interface ConvertibleFile {
  id: number
  fileName: string
  fileType: string
  fileSize: number
  uploaderName: string
  createTime: string
}

export interface ApprovalRequestVO {
  id: number
  applicantId: number
  documentId: number
  title: string
  type: string
  status: number
  createTime: string
}

export function getConvertibleFiles() {
  return request.get<{ code: number; data: ConvertibleFile[] }>('/workflow/approval/convertible-files')
}

export function submitApproval(data: { documentId: number; title: string; type?: string }) {
  return request.post<{ code: number; data: { id: number; status: number; message: string } }>('/workflow/approval/submit', data)
}

export function getMyApprovalRequests() {
  return request.get<{ code: number; data: ApprovalRequestVO[] }>('/workflow/approval/my-requests')
}

export interface DirectoryNode {
  id: number
  dirName: string
  parentId: number | null
  children?: DirectoryNode[]
}

export function getDirectoryTree() {
  return request.get<{ code: number; data: DirectoryNode[] }>('/front/directories')
}

export function approveAndStamp(approvalId: number, directoryId: number, signer?: string) {
  return request.post<{ code: number; data: { approvalId: number; stampedFileId: number; stampedFileName: string; message: string } }>(
    `/workflow/approval/approve/${approvalId}?directoryId=${directoryId}${signer ? '&signer=' + encodeURIComponent(signer) : ''}`,
    undefined,
    { timeout: 300000 }
  )
}
