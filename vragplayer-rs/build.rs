fn main() {
    println!("cargo:rerun-if-changed=build.rs");

    let target_os = std::env::var("CARGO_CFG_TARGET_OS").unwrap_or_default();
    if target_os == "android" {
        // oboe-sys (via cpal) links libc++_static.a on Android without libc++abi.a.
        // Emit c++_static followed by c++abi so all C++ ABI symbols (__cxa_guard_acquire,
        // operator new/delete, __gxx_personality_v0, std::terminate, etc.) resolve cleanly
        // before libunwind on NDK 27 for both aarch64 and armv7.
        println!("cargo:rustc-link-lib=c++_static");
        println!("cargo:rustc-link-lib=c++abi");
    }
}
