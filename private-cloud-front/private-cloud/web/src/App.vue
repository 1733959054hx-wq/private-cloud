<template>
  <router-view />
</template>

<script setup lang="ts">
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html, body, #app {
  height: 100%;
  font-family: 'Helvetica Neue', Helvetica, 'PingFang SC', 'Microsoft YaHei', Arial, sans-serif;
}

.markdown-body {
  font-size: 14px;
  line-height: 1.7;
  color: #303133;
}

.markdown-body h1,
.markdown-body h2,
.markdown-body h3,
.markdown-body h4 {
  margin: 16px 0 8px;
  font-weight: 600;
}

.markdown-body h1 { font-size: 22px; }
.markdown-body h2 { font-size: 18px; }
.markdown-body h3 { font-size: 16px; }

.markdown-body p {
  margin: 8px 0;
}

.markdown-body ul,
.markdown-body ol {
  padding-left: 24px;
  margin: 8px 0;
}

.markdown-body code {
  background: #f5f7fa;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 13px;
  color: #c7254e;
}

.markdown-body pre {
  background: #f5f7fa;
  padding: 16px;
  border-radius: 8px;
  overflow-x: auto;
  margin: 12px 0;
}

.markdown-body pre code {
  background: none;
  padding: 0;
  color: inherit;
}

.markdown-body blockquote {
  border-left: 4px solid #409EFF;
  padding: 8px 16px;
  margin: 12px 0;
  background: #ecf5ff;
  border-radius: 0 8px 8px 0;
  color: #606266;
}

.markdown-body table {
  width: 100%;
  border-collapse: collapse;
  margin: 12px 0;
}

.markdown-body th,
.markdown-body td {
  border: 1px solid #e4e7ed;
  padding: 8px 12px;
  text-align: left;
}

.markdown-body th {
  background: #f5f7fa;
  font-weight: 600;
}

.markdown-body a {
  color: #409EFF;
  text-decoration: none;
}

.markdown-body a:hover {
  text-decoration: underline;
}
</style>
