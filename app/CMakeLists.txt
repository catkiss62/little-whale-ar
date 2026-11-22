cmake_minimum_required(VERSION 3.22.1)
project(camera_frame)
# FrameConverter looks up this soname. Only the camera conversion JNI is needed.
add_library(glb_renderer SHARED ../upstream/app/src/main/cpp/frame/frame_convert_jni.cpp)
target_compile_features(glb_renderer PRIVATE cxx_std_17)
target_link_options(glb_renderer PRIVATE "-Wl,-z,max-page-size=16384")
target_link_libraries(glb_renderer android log jnigraphics)
