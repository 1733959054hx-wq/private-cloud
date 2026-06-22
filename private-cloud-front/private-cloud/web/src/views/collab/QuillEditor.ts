import Quill from 'quill'
import QuillCursors from 'quill-cursors'
import 'quill/dist/quill.snow.css'

Quill.register('modules/cursors', QuillCursors)

export class QuillEditor {
  quillInstance: Quill

  constructor(editorContainer: HTMLElement) {
    this.quillInstance = new Quill(editorContainer, {
      theme: 'snow',
      placeholder: '开始协作编辑...',
      modules: {
        toolbar: [
          [{ header: [1, 2, 3, false] }],
          ['bold', 'italic', 'underline', 'strike'],
          [{ color: [] }, { background: [] }],
          [{ list: 'ordered' }, { list: 'bullet' }],
          ['blockquote', 'code-block'],
          [{ indent: '-1' }, { indent: '+1' }],
          [{ align: [] }],
          ['link', 'image'],
          ['clean'],
        ],
        cursors: true,
        history: {
          userOnly: true,
        },
      },
    })
  }

  getInstance(): Quill {
    return this.quillInstance
  }

  setReadonly(readonly: boolean) {
    this.quillInstance.enable(!readonly)
  }

  destroy() {
    // 移除编辑器 DOM
    const container = this.quillInstance.container
    if (container) {
      container.innerHTML = ''
    }
  }
}
