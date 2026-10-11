# Copyright (c) Meta Platforms, Inc. and affiliates.
# All rights reserved.
#
# This source code is licensed under the license found in the
# LICENSE file in the root directory of this source tree.

# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# sherpa-onnx (lib/sherpa-onnx-*.aar, the wake-word spotter): its native code
# reads the Kotlin config classes' fields by name through JNI, and the local
# AAR ships no consumer rules. Without this, R8 renames those fields and every
# minified release crashes on launch with
# NoSuchFieldError: "maxActivePaths" in KeywordSpotterConfig.
-keep class com.k2fsa.sherpa.onnx.** { *; }
