# Shrink and optimise, but keep names and line numbers so crash reports read without a retrace step.
# (The repo is public and the app holds no secrets in code, so readable names cost nothing.)
-dontobfuscate
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod
