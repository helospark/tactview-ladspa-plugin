#include "global.h"

#ifdef __linux__ 
#include <dlfcn.h>
#elif _WIN32 || __CYGWIN__
#include <windows.h>
#endif
#include <iostream>
#include <cstdint>

void* getFunction(void* handle, const char* functionName) {
    void* result = NULL;
    #ifdef __linux__
        result = dlsym(handle, functionName);
        if (!result) {
            LOG_ERROR("Unable to find " << functionName << " function" << dlerror());
            return NULL;
        }
    #elif _WIN32 || __CYGWIN__
        result = (intFptr) GetProcAddress((HINSTANCE)handle, functionName);
        if (!result) {
            LOG_ERROR("Unable to find " << functionName << " function");
            return NULL;
        }
    #endif
    return result;
}