# --- GeckoView 核心规则 ---
# GeckoView 内部有很多反射调用，千万不能混淆，否则网页会打不开
-keep class org.mozilla.geckoview.** { *; }

# --- 修复 R8 编译报错 ---
# 忽略 java.beans 等桌面端 Java 类的警告 (SnakeYAML 依赖导致的)
-dontwarn java.beans.**
-dontwarn java.awt.**
-dontwarn org.yaml.snakeyaml.**

# --- 保护你的代码 ---
# 保护主程序入口不被混淆
-keep class com.kero.browser.** { *; }

# 保持 native 方法不被混淆
-keepclasseswithmembernames class * {
    native <methods>;
}
