# 图表生成指南

## 自动导出 SVG (推荐)

### 方法 1：在线工具 (最简单)
1. 打开 https://mermaid.live
2. 将 `policy-flow.md` 中的 mermaid 代码复制到编辑框
3. 点击右上角 "Export" → "SVG"
4. 保存为 `policy-flow.svg`

### 方法 2：VS Code (需要扩展)
1. 安装扩展 `Markdown Preview Mermaid Support` (百万级下载)
2. 打开 `policy-flow.md`
3. 右键 → "Export Mermaid Diagram" → 选择 SVG 格式

### 方法 3：命令行 (需要 Node.js)
```bash
npm install -g @mermaid-js/mermaid-cli
mmdc -i policy-flow.md -o policy-flow.svg -t dark
```

## 文件结构

```
docs/
├── policy-flow.md              ← 编辑这个文件（mermaid 源代码）
├── diagrams/
│   └── policy-flow.svg         ← 导出的图片（用于文档和 README）
└── EXPORT_GUIDE.md             ← 本文件
```

## 修改流程

1. 编辑 `policy-flow.md` 中的 mermaid 代码
2. 保存并 commit
3. 需要时重新导出 SVG 并更新 `policy-flow.svg`
4. commit 两个文件

## 在 README 中引用

```markdown
![Policy Module Flow](docs/diagrams/policy-flow.svg)
```

## 注意

- `.md` 文件便于版本控制和团队协作
- `.svg` 文件用于快速查看和文档展示
- GitHub 会自动渲染 mermaid 代码块，所以 .md 文件在 GitHub 上也能直接看
