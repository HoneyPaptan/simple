package sh.simple.android.ui.kit

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import sh.simple.android.ui.theme.SimpleColors

object IconsGenerated {
    val Plus = ImageVector.Builder("Plus", 24.dp, 24.dp, 24f, 24f).apply {
        val strokeColor = SolidColor(SimpleColors.iconVectorInk)
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M12.001 4.00101V20.001").toNodes())
        }
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M20.001 12.001L4.00098 12.001").toNodes())
        }
    }.build()

    val Search = ImageVector.Builder("Search", 24.dp, 24.dp, 24f, 24f).apply {
        val strokeColor = SolidColor(SimpleColors.iconVectorInk)
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M17 17L21 21").toNodes())
        }
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M19 11C19 6.58172 15.4183 3 11 3C6.58172 3 3 6.58172 3 11C3 15.4183 6.58172 19 11 19C15.4183 19 19 15.4183 19 11Z").toNodes())
        }
    }.build()

    val Mic = ImageVector.Builder("Mic", 24.dp, 24.dp, 24f, 24f).apply {
        val strokeColor = SolidColor(SimpleColors.iconVectorInk)
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M7 6.5C7 4.01472 9.01472 2 11.5 2C13.9853 2 16 4.01472 16 6.5V11.5C16 13.9853 13.9853 16 11.5 16C9.01472 16 7 13.9853 7 11.5V6.5Z").toNodes())
        }
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M11.5 19H11.0828C7.57267 19 4.57706 16.4623 4 13M11.5 19H11.9172C15.4273 19 18.4229 16.4623 19 13M11.5 19V22").toNodes())
        }
    }.build()

    val X = ImageVector.Builder("X", 24.dp, 24.dp, 24f, 24f).apply {
        val strokeColor = SolidColor(SimpleColors.iconVectorInk)
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M18.9991 19L5 5.00098").toNodes())
        }
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M19 5L5.00092 18.999").toNodes())
        }
    }.build()

    val Check = ImageVector.Builder("Check", 24.dp, 24.dp, 24f, 24f).apply {
        val strokeColor = SolidColor(SimpleColors.iconVectorInk)
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M5 14.5C5 14.5 6.5 14.5 8.5 18C8.5 18 14.0588 8.83333 19 7").toNodes())
        }
    }.build()

    val ChevR = ImageVector.Builder("ChevR", 24.dp, 24.dp, 24f, 24f).apply {
        val strokeColor = SolidColor(SimpleColors.iconVectorInk)
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M9.00005 6C9.00005 6 15 10.4189 15 12C15 13.5812 9 18 9 18").toNodes())
        }
    }.build()

    val ChevD = ImageVector.Builder("ChevD", 24.dp, 24.dp, 24f, 24f).apply {
        val strokeColor = SolidColor(SimpleColors.iconVectorInk)
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M18 9.00005C18 9.00005 13.5811 15 12 15C10.4188 15 6 9 6 9").toNodes())
        }
    }.build()

    val Back = ImageVector.Builder("Back", 24.dp, 24.dp, 24f, 24f).apply {
        val strokeColor = SolidColor(SimpleColors.iconVectorInk)
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M15 6C15 6 9.00001 10.4189 9 12C8.99999 13.5812 15 18 15 18").toNodes())
        }
    }.build()

    val More = ImageVector.Builder("More", 24.dp, 24.dp, 24f, 24f).apply {
        val strokeColor = SolidColor(SimpleColors.iconVectorInk)
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M21 12C21 11.1716 20.3284 10.5 19.5 10.5C18.6716 10.5 18 11.1716 18 12C18 12.8284 18.6716 13.5 19.5 13.5C20.3284 13.5 21 12.8284 21 12Z").toNodes())
        }
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M13.5 12C13.5 11.1716 12.8284 10.5 12 10.5C11.1716 10.5 10.5 11.1716 10.5 12C10.5 12.8284 11.1716 13.5 12 13.5C12.8284 13.5 13.5 12.8284 13.5 12Z").toNodes())
        }
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M6 12C6 11.1716 5.32843 10.5 4.5 10.5C3.67157 10.5 3 11.1716 3 12C3 12.8284 3.67157 13.5 4.5 13.5C5.32843 13.5 6 12.8284 6 12Z").toNodes())
        }
    }.build()

    val Folder = ImageVector.Builder("Folder", 24.dp, 24.dp, 24f, 24f).apply {
        val strokeColor = SolidColor(SimpleColors.iconVectorInk)
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M13.6656 6.49446H16C18.8284 6.49446 20.2426 6.49446 21.1213 7.37314C22 8.25182 22 9.66603 22 12.4945V14.5C22 17.3284 22 18.7426 21.1213 19.6213C20.2426 20.5 18.8284 20.5 16 20.5H8C5.17157 20.5 3.75736 20.5 2.87868 19.6213C2 18.7426 2 17.3284 2 14.5V8.99183C2 6.65409 2 5.48521 2.61604 4.67881C2.7778 4.46706 2.96706 4.2778 3.17881 4.11604C3.98521 3.5 5.15409 3.5 7.49183 3.5C8.16633 3.5 8.50358 3.5 8.81392 3.60442C8.8976 3.63257 8.97932 3.66623 9.05854 3.70518C9.35239 3.84965 9.59179 4.08719 10.0706 4.56227L10.8483 5.33389C11.4254 5.9066 11.714 6.19295 12.08 6.3437C12.446 6.49446 12.8525 6.49446 13.6656 6.49446Z").toNodes())
        }
    }.build()

    val Folders = ImageVector.Builder("Folders", 24.dp, 24.dp, 24f, 24f).apply {
        val strokeColor = SolidColor(SimpleColors.iconVectorInk)
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M10.7425 10.9976H12.9999C14.8855 10.9976 15.8283 10.9976 16.4141 11.5833C16.9999 12.1691 16.9999 13.1119 16.9999 14.9976V17.4976C16.9999 19.3832 16.9999 20.326 16.4141 20.9118C15.8283 21.4976 14.8855 21.4976 12.9999 21.4976H5.99991C4.11429 21.4976 3.17148 21.4976 2.58569 20.9118C1.99991 20.326 1.99991 19.3832 1.99991 17.4976V12.3124C1.99991 10.6062 1.99991 9.75308 2.48671 9.1898C2.55012 9.11643 2.61878 9.04777 2.69215 8.98436C3.25543 8.49756 4.10855 8.49756 5.81478 8.49756C6.37074 8.49756 6.64871 8.49756 6.90187 8.59179C6.93594 8.60448 6.96954 8.61839 7.0026 8.63352C7.24824 8.74589 7.4448 8.94245 7.83792 9.33557L8.62123 10.1189C9.05477 10.5524 9.27155 10.7692 9.5472 10.8834C9.82286 10.9976 10.1294 10.9976 10.7425 10.9976Z").toNodes())
        }
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M20.4999 15.4976C20.8872 15.4133 21.1793 15.2726 21.4141 15.0378C21.9999 14.452 21.9999 13.5092 21.9999 11.6236V8.99756C21.9999 7.11195 21.9999 6.16914 21.4141 5.58335C20.8283 4.99756 19.8855 4.99756 17.9999 4.99756L15.725 5.0021C15.1118 5.0021 14.8053 5.0021 14.5296 4.88792C14.254 4.77374 14.0372 4.55696 13.6037 4.12342L12.8203 3.34011C12.4272 2.94699 12.2307 2.75043 11.985 2.63806C11.952 2.62293 11.9184 2.60902 11.8843 2.59634C11.6311 2.5021 11.3532 2.5021 10.7972 2.5021C9.09097 2.5021 8.23785 2.5021 7.67458 2.9889C7.60121 3.05231 7.53254 3.12097 7.46913 3.19435C7.14276 3.572 7.0352 4.07993 6.99976 4.88792").toNodes())
        }
    }.build()

    val Bell = ImageVector.Builder("Bell", 24.dp, 24.dp, 24f, 24f).apply {
        val strokeColor = SolidColor(SimpleColors.iconVectorInk)
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M15.5 18C15.5 19.933 13.933 21.5 12 21.5C10.067 21.5 8.5 19.933 8.5 18").toNodes())
        }
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M19.2311 18H4.76887C3.79195 18 3 17.208 3 16.2311C3 15.762 3.18636 15.3121 3.51809 14.9803L4.12132 14.3771C4.68393 13.8145 5 13.0514 5 12.2558V9.5C5 5.63401 8.13401 2.5 12 2.5C15.866 2.5 19 5.634 19 9.5V12.2558C19 13.0514 19.3161 13.8145 19.8787 14.3771L20.4819 14.9803C20.8136 15.3121 21 15.762 21 16.2311C21 17.208 20.208 18 19.2311 18Z").toNodes())
        }
    }.build()

    val Copy = ImageVector.Builder("Copy", 24.dp, 24.dp, 24f, 24f).apply {
        val strokeColor = SolidColor(SimpleColors.iconVectorInk)
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M7.5 14.5C7.5 11.2002 7.5 9.55025 8.52513 8.52513C9.55025 7.5 11.2002 7.5 14.5 7.5C17.7998 7.5 19.4497 7.5 20.4749 8.52513C21.5 9.55025 21.5 11.2002 21.5 14.5C21.5 17.7998 21.5 19.4497 20.4749 20.4749C19.4497 21.5 17.7998 21.5 14.5 21.5C11.2002 21.5 9.55025 21.5 8.52513 20.4749C7.5 19.4497 7.5 17.7998 7.5 14.5Z").toNodes())
        }
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M7.5 16.5C6.10355 16.5 5.40533 16.5 4.84402 16.3036C3.83866 15.9518 3.0482 15.1613 2.69641 14.156C2.5 13.5947 2.5 12.8964 2.5 11.5V9.5C2.5 6.20017 2.5 4.55025 3.52513 3.52513C4.55025 2.5 6.20017 2.5 9.5 2.5H11.5C12.8964 2.5 13.5947 2.5 14.156 2.69641C15.1613 3.0482 15.9518 3.83866 16.3036 4.84402C16.5 5.40533 16.5 6.10355 16.5 7.5").toNodes())
        }
    }.build()

    val Trash = ImageVector.Builder("Trash", 24.dp, 24.dp, 24f, 24f).apply {
        val strokeColor = SolidColor(SimpleColors.iconVectorInk)
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M18.8516 16.3743L19.5 6H4.5L5.14839 16.3743C5.315 19.0401 5.39831 20.373 6.26426 21.1865C7.13021 22 8.46571 22 11.1367 22H12.8633C15.5343 22 16.8698 22 17.7357 21.1865C18.6017 20.373 18.685 19.0401 18.8516 16.3743Z").toNodes())
        }
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M16 6H21M16 6L15.3778 4.34082C15.0007 3.33524 14.8122 2.83245 14.4402 2.50292C14.3213 2.39765 14.1904 2.3069 14.0501 2.2326C13.6109 2 13.074 2 12 2C10.926 2 10.3891 2 9.9499 2.2326C9.8096 2.3069 9.67865 2.39765 9.55982 2.50292C9.18783 2.83245 8.99928 3.33524 8.62219 4.34082L8 6M16 6H8M3 6H8").toNodes())
        }
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M9.5 11V17M14.5 11V17").toNodes())
        }
    }.build()

    val Stop = ImageVector.Builder("Stop", 24.dp, 24.dp, 24f, 24f).apply {
        val strokeColor = SolidColor(SimpleColors.iconVectorInk)
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M4 12C4 8.72077 4 7.08116 4.81382 5.91891C5.1149 5.48891 5.48891 5.1149 5.91891 4.81382C7.08116 4 8.72077 4 12 4C15.2792 4 16.9188 4 18.0811 4.81382C18.5111 5.1149 18.8851 5.48891 19.1862 5.91891C20 7.08116 20 8.72077 20 12C20 15.2792 20 16.9188 19.1862 18.0811C18.8851 18.5111 18.5111 18.8851 18.0811 19.1862C16.9188 20 15.2792 20 12 20C8.72077 20 7.08116 20 5.91891 19.1862C5.48891 18.8851 5.1149 18.5111 4.81382 18.0811C4 16.9188 4 15.2792 4 12Z").toNodes())
        }
    }.build()

    val Person = ImageVector.Builder("Person", 24.dp, 24.dp, 24f, 24f).apply {
        val strokeColor = SolidColor(SimpleColors.iconVectorInk)
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M20 21.0001C19.713 17.269 16.7289 14.3151 12.995 14.0662L12 13.9999C11.6446 14.0096 11.3134 14.0225 11.0008 14.0378C7.3 14.2192 4.28417 17.3057 4 21.0001").toNodes())
        }
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M8 6.99988A4 4 0 1 0 16 6.99988A4 4 0 1 0 8 6.99988Z").toNodes())
        }
    }.build()

    val Term = ImageVector.Builder("Term", 24.dp, 24.dp, 24f, 24f).apply {
        val strokeColor = SolidColor(SimpleColors.iconVectorInk)
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M7.5 7.5L8.72654 8.55719C9.24218 9.00163 9.5 9.22386 9.5 9.5C9.5 9.77614 9.24218 9.99836 8.72654 10.4428L7.5 11.5").toNodes())
        }
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M11.5 12.5H15.5").toNodes())
        }
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M12 21C15.7497 21 17.6246 21 18.9389 20.0451C19.3634 19.7367 19.7367 19.3634 20.0451 18.9389C21 17.6246 21 15.7497 21 12C21 8.25027 21 6.3754 20.0451 5.06107C19.7367 4.6366 19.3634 4.26331 18.9389 3.95491C17.6246 3 15.7497 3 12 3C8.25027 3 6.3754 3 5.06107 3.95491C4.6366 4.26331 4.26331 4.6366 3.95491 5.06107C3 6.3754 3 8.25027 3 12C3 15.7497 3 17.6246 3.95491 18.9389C4.26331 19.3634 4.6366 19.7367 5.06107 20.0451C6.3754 21 8.25027 21 12 21Z").toNodes())
        }
    }.build()

    val Grid = ImageVector.Builder("Grid", 24.dp, 24.dp, 24f, 24f).apply {
        val strokeColor = SolidColor(SimpleColors.iconVectorInk)
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M20.1088 3.89124C21.5 5.28249 21.5 7.52166 21.5 12C21.5 16.4783 21.5 18.7175 20.1088 20.1088C18.7175 21.5 16.4783 21.5 12 21.5C7.52166 21.5 5.28249 21.5 3.89124 20.1088C2.5 18.7175 2.5 16.4783 2.5 12C2.5 7.52166 2.5 5.28249 3.89124 3.89124C5.28249 2.5 7.52166 2.5 12 2.5C16.4783 2.5 18.7175 2.5 20.1088 3.89124Z").toNodes())
        }
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M21.5 12L2.5 12").toNodes())
        }
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M12 2.5L12 21.5").toNodes())
        }
    }.build()

    val Branch = ImageVector.Builder("Branch", 24.dp, 24.dp, 24f, 24f).apply {
        val strokeColor = SolidColor(SimpleColors.iconVectorInk)
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M7 19H13C15.8284 19 17.2426 19 18.1213 18.1213C19 17.2426 19 15.8284 19 13V10M19 10C19.7002 10 21.0085 11.9943 21.5 12.5M19 10C18.2998 10 16.9915 11.9943 16.5 12.5").toNodes())
        }
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M5 7L5 17").toNodes())
        }
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M3 5A2 2 0 1 0 7 5A2 2 0 1 0 3 5Z").toNodes())
        }
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M17 5A2 2 0 1 0 21 5A2 2 0 1 0 17 5Z").toNodes())
        }
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M3 19A2 2 0 1 0 7 19A2 2 0 1 0 3 19Z").toNodes())
        }
    }.build()

    val Image = ImageVector.Builder("Image", 24.dp, 24.dp, 24f, 24f).apply {
        val strokeColor = SolidColor(SimpleColors.iconVectorInk)
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M6 7.5A1.5 1.5 0 1 0 9 7.5A1.5 1.5 0 1 0 6 7.5Z").toNodes())
        }
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M2.5 12C2.5 7.52166 2.5 5.28249 3.89124 3.89124C5.28249 2.5 7.52166 2.5 12 2.5C16.4783 2.5 18.7175 2.5 20.1088 3.89124C21.5 5.28249 21.5 7.52166 21.5 12C21.5 16.4783 21.5 18.7175 20.1088 20.1088C18.7175 21.5 16.4783 21.5 12 21.5C7.52166 21.5 5.28249 21.5 3.89124 20.1088C2.5 18.7175 2.5 16.4783 2.5 12Z").toNodes())
        }
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M5 21C9.37246 15.775 14.2741 8.88406 21.4975 13.5424").toNodes())
        }
    }.build()

    val Camera = ImageVector.Builder("Camera", 24.dp, 24.dp, 24f, 24f).apply {
        val strokeColor = SolidColor(SimpleColors.iconVectorInk)
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M12.6974 3.5H11.303C10.5884 3.5 10.2311 3.5 9.91067 3.612C9.71499 3.68039 9.53113 3.77879 9.36568 3.90367C9.09474 4.10816 8.89655 4.40544 8.50018 5L8.50017 5.00001C8.29717 5.30453 7.99794 5.75337 7.87867 5.87871C7.58314 6.18927 7.19563 6.39666 6.77329 6.47029C6.60284 6.5 6.41985 6.5 6.05387 6.5C5.07379 6.5 4.58376 6.5 4.18307 6.61342C3.18074 6.89716 2.39734 7.68055 2.1136 8.68289C2.00018 9.08357 2.00018 9.57361 2.00018 10.5537V14.5C2.00018 17.3284 2.00018 18.7426 2.87886 19.6213C3.75754 20.5 5.17176 20.5 8.00018 20.5H16.0002C18.8286 20.5 20.2428 20.5 21.1215 19.6213C22.0002 18.7426 22.0002 17.3284 22.0002 14.5V10.5537C22.0002 9.57361 22.0002 9.08357 21.8868 8.68289C21.603 7.68055 20.8196 6.89716 19.8173 6.61342C19.4166 6.5 18.9266 6.5 17.9465 6.5C17.5805 6.5 17.3975 6.5 17.2271 6.47029C16.8047 6.39666 16.4172 6.18927 16.1217 5.87871C16.0024 5.75336 15.7032 5.30451 15.5002 5C15.1038 4.40544 14.9056 4.10816 14.6347 3.90367C14.4692 3.77879 14.2854 3.68039 14.0897 3.612C13.7693 3.5 13.412 3.5 12.6974 3.5Z").toNodes())
        }
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M16.0002 13C16.0002 15.2091 14.2093 17 12.0002 17C9.79104 17 8.00018 15.2091 8.00018 13C8.00018 10.7909 9.79104 9 12.0002 9C14.2093 9 16.0002 10.7909 16.0002 13Z").toNodes())
        }
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M19.1252 9.5H19.0002M19.2502 9.5C19.2502 9.63807 19.1383 9.75 19.0002 9.75C18.8621 9.75 18.7502 9.63807 18.7502 9.5C18.7502 9.36193 18.8621 9.25 19.0002 9.25C19.1383 9.25 19.2502 9.36193 19.2502 9.5Z").toNodes())
        }
    }.build()

    val Server = ImageVector.Builder("Server", 24.dp, 24.dp, 24f, 24f).apply {
        val strokeColor = SolidColor(SimpleColors.iconVectorInk)
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M19 4H5C4.06812 4 3.60218 4 3.23463 4.15224C2.74458 4.35523 2.35523 4.74458 2.15224 5.23463C2 5.60218 2 6.06812 2 7C2 7.93188 2 8.39782 2.15224 8.76537C2.35523 9.25542 2.74458 9.64477 3.23463 9.84776C3.60218 10 4.06812 10 5 10H19C19.9319 10 20.3978 10 20.7654 9.84776C21.2554 9.64477 21.6448 9.25542 21.8478 8.76537C22 8.39782 22 7.93188 22 7C22 6.06812 22 5.60218 21.8478 5.23463C21.6448 4.74458 21.2554 4.35523 20.7654 4.15224C20.3978 4 19.9319 4 19 4Z").toNodes())
        }
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M19 14H5C4.06812 14 3.60218 14 3.23463 14.1522C2.74458 14.3552 2.35523 14.7446 2.15224 15.2346C2 15.6022 2 16.0681 2 17C2 17.9319 2 18.3978 2.15224 18.7654C2.35523 19.2554 2.74458 19.6448 3.23463 19.8478C3.60218 20 4.06812 20 5 20H19C19.9319 20 20.3978 20 20.7654 19.8478C21.2554 19.6448 21.6448 19.2554 21.8478 18.7654C22 18.3978 22 17.9319 22 17C22 16.0681 22 15.6022 21.8478 15.2346C21.6448 14.7446 21.2554 14.3552 20.7654 14.1522C20.3978 14 19.9319 14 19 14Z").toNodes())
        }
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M6.125 7H6M6.25 7C6.25 7.13807 6.13807 7.25 6 7.25C5.86193 7.25 5.75 7.13807 5.75 7C5.75 6.86193 5.86193 6.75 6 6.75C6.13807 6.75 6.25 6.86193 6.25 7Z").toNodes())
        }
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M10.125 7H10M10.25 7C10.25 7.13807 10.1381 7.25 10 7.25C9.86193 7.25 9.75 7.13807 9.75 7C9.75 6.86193 9.86193 6.75 10 6.75C10.1381 6.75 10.25 6.86193 10.25 7Z").toNodes())
        }
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M6.125 17H6M6.25 17C6.25 17.1381 6.13807 17.25 6 17.25C5.86193 17.25 5.75 17.1381 5.75 17C5.75 16.8619 5.86193 16.75 6 16.75C6.13807 16.75 6.25 16.8619 6.25 17Z").toNodes())
        }
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M10.125 17H10M10.25 17C10.25 17.1381 10.1381 17.25 10 17.25C9.86193 17.25 9.75 17.1381 9.75 17C9.75 16.8619 9.86193 16.75 10 16.75C10.1381 16.75 10.25 16.8619 10.25 17Z").toNodes())
        }
    }.build()

    val Refresh = ImageVector.Builder("Refresh", 24.dp, 24.dp, 24f, 24f).apply {
        val strokeColor = SolidColor(SimpleColors.iconVectorInk)
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M20.0092 2V5.13219C20.0092 5.42605 19.6418 5.55908 19.4537 5.33333C17.6226 3.2875 14.9617 2 12 2C6.47715 2 2 6.47715 2 12C2 17.5228 6.47715 22 12 22C17.5228 22 22 17.5228 22 12").toNodes())
        }
    }.build()

    val Pencil = ImageVector.Builder("Pencil", 24.dp, 24.dp, 24f, 24f).apply {
        val strokeColor = SolidColor(SimpleColors.iconVectorInk)
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M15.2141 5.98239L16.6158 4.58063C17.39 3.80646 18.6452 3.80646 19.4194 4.58063C20.1935 5.3548 20.1935 6.60998 19.4194 7.38415L18.0176 8.78591M15.2141 5.98239L6.98023 14.2163C5.93493 15.2616 5.41226 15.7842 5.05637 16.4211C4.70047 17.058 4.3424 18.5619 4 20C5.43809 19.6576 6.94199 19.2995 7.57889 18.9436C8.21579 18.5877 8.73844 18.0651 9.78375 17.0198L18.0176 8.78591M15.2141 5.98239L18.0176 8.78591").toNodes())
        }
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M11 20H17").toNodes())
        }
    }.build()

    val Eye = ImageVector.Builder("Eye", 24.dp, 24.dp, 24f, 24f).apply {
        val strokeColor = SolidColor(SimpleColors.iconVectorInk)
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M21.544 11.045C21.848 11.4713 22 11.6845 22 12C22 12.3155 21.848 12.5287 21.544 12.955C20.1779 14.8706 16.6892 19 12 19C7.31078 19 3.8221 14.8706 2.45604 12.955C2.15201 12.5287 2 12.3155 2 12C2 11.6845 2.15201 11.4713 2.45604 11.045C3.8221 9.12944 7.31078 5 12 5C16.6892 5 20.1779 9.12944 21.544 11.045Z").toNodes())
        }
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M15 12C15 10.3431 13.6569 9 12 9C10.3431 9 9 10.3431 9 12C9 13.6569 10.3431 15 12 15C13.6569 15 15 13.6569 15 12Z").toNodes())
        }
    }.build()

    val Qr = ImageVector.Builder("Qr", 24.dp, 24.dp, 24f, 24f).apply {
        val strokeColor = SolidColor(SimpleColors.iconVectorInk)
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M3 6C3 4.58579 3 3.87868 3.43934 3.43934C3.87868 3 4.58579 3 6 3C7.41421 3 8.12132 3 8.56066 3.43934C9 3.87868 9 4.58579 9 6C9 7.41421 9 8.12132 8.56066 8.56066C8.12132 9 7.41421 9 6 9C4.58579 9 3.87868 9 3.43934 8.56066C3 8.12132 3 7.41421 3 6Z").toNodes())
        }
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M3 18C3 16.5858 3 15.8787 3.43934 15.4393C3.87868 15 4.58579 15 6 15C7.41421 15 8.12132 15 8.56066 15.4393C9 15.8787 9 16.5858 9 18C9 19.4142 9 20.1213 8.56066 20.5607C8.12132 21 7.41421 21 6 21C4.58579 21 3.87868 21 3.43934 20.5607C3 20.1213 3 19.4142 3 18Z").toNodes())
        }
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M3 12L9 12").toNodes())
        }
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M12 3V8").toNodes())
        }
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M15 6C15 4.58579 15 3.87868 15.4393 3.43934C15.8787 3 16.5858 3 18 3C19.4142 3 20.1213 3 20.5607 3.43934C21 3.87868 21 4.58579 21 6C21 7.41421 21 8.12132 20.5607 8.56066C20.1213 9 19.4142 9 18 9C16.5858 9 15.8787 9 15.4393 8.56066C15 8.12132 15 7.41421 15 6Z").toNodes())
        }
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M21 12H15C13.5858 12 12.8787 12 12.4393 12.4393C12 12.8787 12 13.5858 12 15M12 17.7692V20.5385M15 15V16.5C15 17.9464 15.7837 18 17 18C17.5523 18 18 18.4477 18 19M16 21H15M18 15C19.4142 15 20.1213 15 20.5607 15.44C21 15.8799 21 16.5881 21 18.0043C21 19.4206 21 20.1287 20.5607 20.5687C20.24 20.8898 19.7767 20.9766 19 21").toNodes())
        }
    }.build()

    val Pin = ImageVector.Builder("Pin", 24.dp, 24.dp, 24f, 24f).apply {
        val strokeColor = SolidColor(SimpleColors.iconVectorInk)
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M3 21L8 16").toNodes())
        }
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M13.2585 18.8714C9.51516 18.0215 5.97844 14.4848 5.12853 10.7415C4.99399 10.1489 4.92672 9.85266 5.12161 9.37197C5.3165 8.89129 5.55457 8.74255 6.03071 8.44509C7.10705 7.77265 8.27254 7.55888 9.48209 7.66586C11.1793 7.81598 12.0279 7.89104 12.4512 7.67048C12.8746 7.44991 13.1622 6.93417 13.7376 5.90269L14.4664 4.59604C14.9465 3.73528 15.1866 3.3049 15.7513 3.10202C16.316 2.89913 16.6558 3.02199 17.3355 3.26771C18.9249 3.84236 20.1576 5.07505 20.7323 6.66449C20.978 7.34417 21.1009 7.68401 20.898 8.2487C20.6951 8.8134 20.2647 9.05346 19.4039 9.53358L18.0672 10.2792C17.0376 10.8534 16.5229 11.1406 16.3024 11.568C16.0819 11.9955 16.162 12.8256 16.3221 14.4859C16.4399 15.7068 16.2369 16.88 15.5555 17.9697C15.2577 18.4458 15.1088 18.6839 14.6283 18.8786C14.1477 19.0733 13.8513 19.006 13.2585 18.8714Z").toNodes())
        }
    }.build()

    val Arrow = ImageVector.Builder("Arrow", 24.dp, 24.dp, 24f, 24f).apply {
        val strokeColor = SolidColor(SimpleColors.iconVectorInk)
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M9 6.65032C9 6.65032 15.9383 6.10759 16.9154 7.08463C17.8924 8.06167 17.3496 15 17.3496 15M16.5 7.5L6.5 17.5").toNodes())
        }
    }.build()

    val Shield = ImageVector.Builder("Shield", 24.dp, 24.dp, 24f, 24f).apply {
        val strokeColor = SolidColor(SimpleColors.iconVectorInk)
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M18.7088 3.49534C16.8165 2.55382 14.5009 2 12 2C9.4991 2 7.1835 2.55382 5.29116 3.49534C4.36318 3.95706 3.89919 4.18792 3.4496 4.91378C3 5.63965 3 6.34248 3 7.74814V11.2371C3 16.9205 7.54236 20.0804 10.173 21.4338C10.9067 21.8113 11.2735 22 12 22C12.7265 22 13.0933 21.8113 13.8269 21.4338C16.4576 20.0804 21 16.9205 21 11.2371L21 7.74814C21 6.34249 21 5.63966 20.5504 4.91378C20.1008 4.18791 19.6368 3.95706 18.7088 3.49534Z").toNodes())
        }
    }.build()

    val FolderPlus = ImageVector.Builder("FolderPlus", 24.dp, 24.dp, 24f, 24f).apply {
        val strokeColor = SolidColor(SimpleColors.iconVectorInk)
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M13 20H8C5.17157 20 3.75736 20 2.87868 19.1213C2 18.2426 2 16.8284 2 14V8.49183C2 6.15409 2 4.98521 2.61604 4.17881C2.7778 3.96706 2.96706 3.7778 3.17881 3.61604C3.98521 3 5.15409 3 7.49183 3C8.16633 3 8.50358 3 8.81392 3.10442C8.8976 3.13257 8.97932 3.16623 9.05854 3.20518C9.35239 3.34965 9.59179 3.58719 10.0706 4.06227L10.8483 4.83389C11.4254 5.4066 11.714 5.69295 12.08 5.8437C12.446 5.99446 12.8525 5.99446 13.6656 5.99446H16C18.8284 5.99446 20.2426 5.99446 21.1213 6.87314C22 7.75182 22 9.16603 22 11.9945V12.5").toNodes())
        }
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M19 15V21M22 18H16").toNodes())
        }
    }.build()

    val Up = ImageVector.Builder("Up", 24.dp, 24.dp, 24f, 24f).apply {
        val strokeColor = SolidColor(SimpleColors.iconVectorInk)
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M17.9998 15C17.9998 15 13.5809 9.00001 11.9998 9C10.4187 8.99999 5.99985 15 5.99985 15").toNodes())
        }
    }.build()

    val Down = ImageVector.Builder("Down", 24.dp, 24.dp, 24f, 24f).apply {
        val strokeColor = SolidColor(SimpleColors.iconVectorInk)
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M18 9.00005C18 9.00005 13.5811 15 12 15C10.4188 15 6 9 6 9").toNodes())
        }
    }.build()

    val Sliders = ImageVector.Builder("Sliders", 24.dp, 24.dp, 24f, 24f).apply {
        val strokeColor = SolidColor(SimpleColors.iconVectorInk)
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M2.99982 12.0002H10.9998").toNodes())
        }
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M13.9998 5.00015L20.9998 5.00015").toNodes())
        }
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M11.9998 19.0002L20.9998 19.0002").toNodes())
        }
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M2.99982 5.00015H9.99982M9.99982 5.00015V3.00015M9.99982 5.00015V7.00015").toNodes())
        }
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M2.99982 19.0002H7.99982M7.99982 19.0002V17.0002M7.99982 19.0002V21.0002").toNodes())
        }
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M14.9998 12.0002L20.9998 12.0002M14.9998 12.0002V10.0002M14.9998 12.0002V14.0002").toNodes())
        }
    }.build()

    val Bolt = ImageVector.Builder("Bolt", 24.dp, 24.dp, 24f, 24f).apply {
        val strokeColor = SolidColor(SimpleColors.iconVectorInk)
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M5.22576 11.3294L12.224 2.34651C12.7713 1.64397 13.7972 2.08124 13.7972 3.01707V9.96994C13.7972 10.5305 14.1995 10.985 14.6958 10.985H18.0996C18.8729 10.985 19.2851 12.0149 18.7742 12.6706L11.776 21.6535C11.2287 22.356 10.2028 21.9188 10.2028 20.9829V14.0301C10.2028 13.4695 9.80048 13.015 9.3042 13.015H5.90035C5.12711 13.015 4.71494 11.9851 5.22576 11.3294Z").toNodes())
        }
    }.build()

    val File = ImageVector.Builder("File", 24.dp, 24.dp, 24f, 24f).apply {
        val strokeColor = SolidColor(SimpleColors.iconVectorInk)
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M8 7L16 7").toNodes())
        }
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M8 11L12 11").toNodes())
        }
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M13 21.5V21C13 18.1716 13 16.7574 13.8787 15.8787C14.7574 15 16.1716 15 19 15H19.5M20 13.3431V10C20 6.22876 20 4.34315 18.8284 3.17157C17.6569 2 15.7712 2 12 2C8.22877 2 6.34315 2 5.17157 3.17157C4 4.34314 4 6.22876 4 10L4 14.5442C4 17.7892 4 19.4117 4.88607 20.5107C5.06508 20.7327 5.26731 20.9349 5.48933 21.1139C6.58831 22 8.21082 22 11.4558 22C12.1614 22 12.5141 22 12.8372 21.886C12.9044 21.8623 12.9702 21.835 13.0345 21.8043C13.3436 21.6564 13.593 21.407 14.0919 20.9081L18.8284 16.1716C19.4065 15.5935 19.6955 15.3045 19.8478 14.9369C20 14.5694 20 14.1606 20 13.3431Z").toNodes())
        }
    }.build()

    val Text = ImageVector.Builder("Text", 24.dp, 24.dp, 24f, 24f).apply {
        val strokeColor = SolidColor(SimpleColors.iconVectorInk)
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M15 21.001H9").toNodes())
        }
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M12 3.00001V21.0008M12 3.00001C13.3874 3.00001 15.1695 3.03055 16.5884 3.17649C17.1885 3.2382 17.4886 3.26906 17.7541 3.37791C18.3066 3.60429 18.7518 4.10063 18.9194 4.67681C19 4.95382 19 5.26992 19 5.90215M12 3.00001C10.6126 3.00001 8.83047 3.03055 7.41161 3.17649C6.8115 3.2382 6.51144 3.26906 6.24586 3.37791C5.69344 3.60429 5.24816 4.10063 5.08057 4.67681C5 4.95382 5 5.26992 5 5.90215").toNodes())
        }
    }.build()

    val Undo = ImageVector.Builder("Undo", 24.dp, 24.dp, 24f, 24f).apply {
        val strokeColor = SolidColor(SimpleColors.iconVectorInk)
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M3 12C3 16.9706 7.02944 21 12 21C16.9706 21 21 16.9706 21 12C21 7.02944 16.9706 3 12 3C8.79275 3 5.97733 4.67765 4.38341 7.20327M3.29339 3.00076L3.46556 5.05452C3.58958 6.53384 3.65159 7.27349 4.13359 7.68914C4.61559 8.10479 5.32673 8.03185 6.74899 7.88595L8.79339 7.67625").toNodes())
        }
    }.build()

    val Redo = ImageVector.Builder("Redo", 24.dp, 24.dp, 24f, 24f).apply {
        val strokeColor = SolidColor(SimpleColors.iconVectorInk)
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M21 12C21 16.9706 16.9706 21 12 21C7.02944 21 3 16.9706 3 12C3 7.02944 7.02944 3 12 3C15.2073 3 18.0227 4.67765 19.6166 7.20327M20.7066 3.00076L20.5344 5.05452C20.4104 6.53384 20.3484 7.27349 19.8664 7.68914C19.3844 8.10479 18.6733 8.03185 17.251 7.88595L15.2066 7.67625").toNodes())
        }
    }.build()

    val Rect = ImageVector.Builder("Rect", 24.dp, 24.dp, 24f, 24f).apply {
        val strokeColor = SolidColor(SimpleColors.iconVectorInk)
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M2.5 12C2.5 7.52166 2.5 5.28249 3.89124 3.89124C5.28249 2.5 7.52166 2.5 12 2.5C16.4783 2.5 18.7175 2.5 20.1088 3.89124C21.5 5.28249 21.5 7.52166 21.5 12C21.5 16.4783 21.5 18.7175 20.1088 20.1088C18.7175 21.5 16.4783 21.5 12 21.5C7.52166 21.5 5.28249 21.5 3.89124 20.1088C2.5 18.7175 2.5 16.4783 2.5 12Z").toNodes())
        }
    }.build()

    val Shell = ImageVector.Builder("Shell", 24.dp, 24.dp, 24f, 24f).apply {
        val strokeColor = SolidColor(SimpleColors.iconVectorInk)
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M17 8L18.8398 9.85008C19.6133 10.6279 20 11.0168 20 11.5C20 11.9832 19.6133 12.3721 18.8398 13.1499L17 15").toNodes())
        }
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M7 8L5.16019 9.85008C4.38673 10.6279 4 11.0168 4 11.5C4 11.9832 4.38673 12.3721 5.16019 13.1499L7 15").toNodes())
        }
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M14.5 4L9.5 20").toNodes())
        }
    }.build()

    val Fx = ImageVector.Builder("Fx", 24.dp, 24.dp, 24f, 24f).apply {
        val strokeColor = SolidColor(SimpleColors.iconVectorInk)
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M17 8L18.8398 9.85008C19.6133 10.6279 20 11.0168 20 11.5C20 11.9832 19.6133 12.3721 18.8398 13.1499L17 15").toNodes())
        }
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M7 8L5.16019 9.85008C4.38673 10.6279 4 11.0168 4 11.5C4 11.9832 4.38673 12.3721 5.16019 13.1499L7 15").toNodes())
        }
        path(
            fill = null,
            stroke = strokeColor,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            appendPathNodes(PathParser().parsePathString("M14.5 4L9.5 20").toNodes())
        }
    }.build()

}
