# ComposePdfViewer

基于 [Android PdfRenderer](https://developer.android.com/reference/android/graphics/pdf/PdfRenderer)
的pdf阅读器，仅支持放大缩小功能。

## 使用方式

将 [PdfViewer.kt](./compose/src/main/java/com/aitsuki/pdfviewer/compose/PdfViewer.kt) 复制到你得项目即可

```kotlin
PdfViewer(pdfFile = file)
```

一些额外的参数解释：

```kotlin
PdfViewer(
    pdfFile = file,
    memoryCacheByteCount = 100 * 1024 * 1024, // bitmap 内存缓存大小，默认100MB
    minPageWidth = 1080, // bitmap 最大和最小宽度（优先使用PdfViewer自身宽度）
    maxPageWidth = 1440, // bitmap 分辨率过大会导致oom，过小则会显示模糊
    pageRatio = sqrt(2), // pdf页面的比例，默认位A4纸的比例（w:h = 1:根号2）
    maxZoom = 3f // 通过手势缩放页面时，限制最大的缩放倍率
)
```

## 参考其它类似项目

https://github.com/afreakyelf/Pdf-Viewer

https://github.com/GRizzi91/bouquet