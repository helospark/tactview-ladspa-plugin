#include "../lv2/lv2/core/lv2.h"
#include "global.h"

#include <iostream>
#include <map>
#include <cstdint>
#include <cstring>

#ifdef __linux__ 
#include <dlfcn.h>
#elif _WIN32 || __CYGWIN__
#include <windows.h>
#endif

LV2_Feature* STRICT_BOUND_FEATURE = new LV2_Feature{"http://lv2plug.in/ns/ext/port-props#supportsStrictBounds", NULL};

LV2_Feature* SUPPORTED_FEATURES[] = {
    STRICT_BOUND_FEATURE,
    NULL
};

struct Lv2IntatiateRequest {
    const char* binaryPath;
    const char* uri;
    int sampleRate;
};
struct Lv2ChannelData {
    int index;
    float* buffer;
};
struct Lv2ParameterData {
    int index;
    float data;
};
struct Lv2RunRequest {
    int instanceId;
    int numberOfChannelData;
    int sampleCount;
    Lv2ChannelData* channels;
    
    int numberOfParameters;
    Lv2ParameterData* parameters;
};

struct InstanceData {
    void* sharedLibraryHandle;
    LV2_Handle handle;
    const LV2_Descriptor* descriptor;

    InstanceData(LV2_Handle handle, const LV2_Descriptor* descriptor, void* sharedLibraryHandle) {
        this->handle = handle;
        this->descriptor = descriptor;
        this->sharedLibraryHandle = sharedLibraryHandle;
    }
};

std::map<std::string, void*> lv2BinaryToHandle;

int globalLv2InstanceId;
std::map<int, InstanceData*> lv2InstanceToHandle;

extern "C" {

    EXPORTED int instantiate(Lv2IntatiateRequest* loadRequest) {
        void *handle;
        
        const char* file = loadRequest->binaryPath;
        std::string fileString = std::string(file);

        LOG("Loading plugin " << file);

        if (lv2BinaryToHandle.find(fileString) != lv2BinaryToHandle.end()) {
            handle = lv2BinaryToHandle[fileString];
        } else {
            #ifdef __linux__
                handle = dlopen(file, RTLD_LAZY);
                if (!handle) {
                    LOG_ERROR("Unable to load native library " << dlerror());
                    return -1;
                }
            #elif _WIN32 || __CYGWIN__
                handle = LoadLibraryA(file); 
                if (!handle) {
                    LOG_ERROR("Unable to load native library" << GetLastError());
                    return -1;
                }
            #endif

            lv2BinaryToHandle[fileString] = handle;
        }

        LV2_Descriptor_Function descriptorFunction = (LV2_Descriptor_Function) getFunction(handle, "lv2_descriptor");

        int i = 0;
        while (true) {
            const LV2_Descriptor* descriptor = descriptorFunction(i);
            if (descriptor == NULL) {
                LOG_ERROR("Cannot find URI " << loadRequest->uri);
                return -1;
            }
            if (strcmp(descriptor->URI, loadRequest->uri) == 0) {
                break;
            }
            ++i;
        }

        const LV2_Descriptor* descriptor = descriptorFunction(i);
        LV2_Handle lv2Handle = descriptor->instantiate(descriptor,loadRequest->sampleRate,loadRequest->binaryPath,SUPPORTED_FEATURES);

        if (lv2Handle == NULL) {
            LOG_ERROR("Failed to instantiate plugin " << loadRequest->uri);
            return -1;
        }

        descriptor->activate(lv2Handle);

        int instanceId = globalLv2InstanceId++;
        lv2InstanceToHandle[instanceId] = new InstanceData(lv2Handle, descriptor, handle);

        return instanceId;
    }

    EXPORTED int run(Lv2RunRequest* runRequest) {
        InstanceData* instanceData = lv2InstanceToHandle[runRequest->instanceId];

        if (instanceData == NULL) {
            return -1;
        }

        const LV2_Descriptor* descriptor = instanceData->descriptor;
        LV2_Handle handle = instanceData->handle;

        for (int i = 0; i < runRequest->numberOfChannelData; ++i) {
            descriptor->connect_port(handle, runRequest->channels[i].index, runRequest->channels[i].buffer);
        }
        for (int i = 0; i < runRequest->numberOfParameters; ++i) {
            descriptor->connect_port(handle, runRequest->parameters[i].index, &runRequest->parameters[i].data);
        }

        descriptor->run(instanceData->handle, runRequest->sampleCount);
    }

    EXPORTED int cleanup(int instanceId) {
        InstanceData* instanceData = lv2InstanceToHandle[instanceId];

        if (instanceData == NULL) {
            LOG_ERROR("Unable to cleanup " << instanceId);
            return -1;
        }
        lv2InstanceToHandle.erase(instanceId);
        instanceData->descriptor->cleanup(instanceData->handle);

        // TODO: check if we can close shared library
    }
}