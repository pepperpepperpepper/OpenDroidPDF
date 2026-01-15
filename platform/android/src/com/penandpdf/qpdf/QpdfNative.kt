package com.penandpdf.qpdf

/**
 * Thin JNI entrypoint for the qpdf shared library.
 *
 * Loads both libqpdf and the JNI shim (libqpdfjni) before exposing the native
 * version string. Any load failure will surface as an UnsatisfiedLinkError so
 * callers can fail fast in instrumentation tests.
 */
object QpdfNative {
    init {
        System.loadLibrary("qpdf")
        System.loadLibrary("qpdfjni")
    }

    external fun version(): String
    external fun run(args: Array<String>): Boolean
    external fun merge(inputA: String, inputB: String, output: String): Boolean
}
