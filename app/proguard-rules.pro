-keep class libXray.** { *; }
-dontwarn libXray.**

# Compact the DEX by moving obfuscated application classes into one package.
# The libXray JNI bridge remains in its original package because of the keep rule above.
-repackageclasses
