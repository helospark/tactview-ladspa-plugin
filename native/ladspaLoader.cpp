#include "../ladspa/ladspa.h"
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

typedef LADSPA_Descriptor* (*ladspa_descriptor_fn)(unsigned long Index);

struct LadspaLibrary {
    void* handle;
    int* loadedIndices;
};

struct LadspaPluginDescriptor {
    void* handle;
    int indexWithinLibrary;
    LADSPA_Descriptor* nativeDescriptor;

    int* loadedIndices;

    // currently only model supported by tactview is 0-2 input and 1 output
    int inputLeftIndex = -1;
    int inputRightIndex = -1;

    int outputLeftIndex = -1;
    int outputRightIndex = -1;
};

struct LadspaInstance {
    LadspaPluginDescriptor* descriptor;
    LADSPA_Handle handle;
};


int globalLadspaLoadedLibraries = 0;
std::map<int, LadspaLibrary*> loadedLadspaLibraries;

int globalLadspaPluginIndex = 0;
std::map<int, LadspaPluginDescriptor*> loadedLadspaPlugins;

int globalLadspaInstanceCount = 0;
std::map<int, LadspaInstance*> instantiatedLadspaPlugins;

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

struct LadspaLoadPluginRequest {
    const char* file;

    // output
    int numberOfLoadedPlugins;
    int* loadedIndices;
};

extern "C" {

    

    EXPORTED int loadPlugin(LadspaLoadPluginRequest* loadRequest) {
        void *handle;
        
        const char* file = loadRequest->file; 

        LOG("Loading plugin " << file);

        #ifdef __linux__
            handle = dlopen(file, RTLD_LAZY);
            if (!handle) {
                LOG_ERROR("Unable to load native library" << dlerror());
                return -1;
            }
        #elif _WIN32 || __CYGWIN__
            handle = LoadLibraryA(file); 
            if (!handle) {
                LOG_ERROR("Unable to load native library" << GetLastError());
                return -1;
            }
        #endif

        LadspaLibrary* library = new LadspaLibrary();
        library->handle = handle;
        loadedLadspaLibraries[globalLadspaLoadedLibraries++] = library;


        ladspa_descriptor_fn descriptorFunction = (ladspa_descriptor_fn)dlsym(handle, "ladspa_descriptor");

        LadspaPluginDescriptor* descriptor = new LadspaPluginDescriptor();

        int numberOfPlugins = 0;
        while (true) {
            LADSPA_Descriptor* nativeDescriptor = descriptorFunction(numberOfPlugins);
            if (!nativeDescriptor) {
                break;
            }
            ++numberOfPlugins;
        }
        library->loadedIndices = new int[numberOfPlugins];

        LOG("Ladspa library " << file << " contains " << numberOfPlugins << "  plugins");

        for (int i = 0; i < numberOfPlugins; ++i) {
            LADSPA_Descriptor* nativeDescriptor = descriptorFunction(i);

            LOG("Loaded " << nativeDescriptor->Name);

            LadspaPluginDescriptor* ladspaDescriptor = new LadspaPluginDescriptor();
            ladspaDescriptor->handle = handle;
            ladspaDescriptor->indexWithinLibrary = i;
            ladspaDescriptor->nativeDescriptor = nativeDescriptor;

            for (int i = 0; i < nativeDescriptor->PortCount; ++i) {
                if (LADSPA_IS_PORT_INPUT(nativeDescriptor->PortDescriptors[i]) && LADSPA_IS_PORT_AUDIO(nativeDescriptor->PortDescriptors[i])) {
                    if (ladspaDescriptor->inputLeftIndex == -1 && (
                        strstr(nativeDescriptor->PortNames[i], "Left") == 0 ||
                        strstr(nativeDescriptor->PortNames[i], "L") == 0)) {
                            ladspaDescriptor->inputLeftIndex = i;
                    } else if (ladspaDescriptor->inputRightIndex == -1 && (
                        strstr(nativeDescriptor->PortNames[i], "Right") == 0 ||
                        strstr(nativeDescriptor->PortNames[i], "R") == 0)) {
                            ladspaDescriptor->inputRightIndex = i;
                    } else {
                        LOG_WARN("Unknown channel type " << nativeDescriptor->PortNames << " assuming left");
                        ladspaDescriptor->inputLeftIndex = i;
                    }
                }
                if (LADSPA_IS_PORT_OUTPUT(nativeDescriptor->PortDescriptors[i]) && LADSPA_IS_PORT_AUDIO(nativeDescriptor->PortDescriptors[i])) {
                    if (ladspaDescriptor->outputLeftIndex == -1 && (
                        strstr(nativeDescriptor->PortNames[i], "Left") == 0 ||
                        strstr(nativeDescriptor->PortNames[i], "L") == 0)) {
                            ladspaDescriptor->outputLeftIndex = i;
                    } else if (ladspaDescriptor->outputRightIndex == -1 && (
                        strstr(nativeDescriptor->PortNames[i], "Right") == 0 ||
                        strstr(nativeDescriptor->PortNames[i], "R") == 0)) {
                            ladspaDescriptor->outputRightIndex = i;
                    } else {
                        LOG_WARN("Unknown channel type " << nativeDescriptor->PortNames << " assuming left");
                        ladspaDescriptor->outputLeftIndex = i;
                    }
                }
            }
            if (ladspaDescriptor->outputLeftIndex == -1 && ladspaDescriptor->outputRightIndex) {
                LOG("No output for " << nativeDescriptor->Name);
                continue; // TODO: fix leak later
            }

            int index = globalLadspaPluginIndex++;

            loadedLadspaPlugins[index] = ladspaDescriptor;

            library->loadedIndices[i] = index;
            ++i;
        }
        loadRequest->numberOfLoadedPlugins = numberOfPlugins;
        loadRequest->loadedIndices = library->loadedIndices;

        return 0;
    }

    struct LadspaGetPluginDescriptionRequest {
        int index;

        // output
        int parameterCount;
        char** parameterNames;
        int* parameterTypes;
        double* parameterLowerValues;
        double* parameterUpperValues;
        
        int channelCount;

        char** name;
        char** label;
    };

    EXPORTED int getParameterNumber(int id) {
        LadspaPluginDescriptor* plugin = loadedLadspaPlugins[id];

        if (!plugin) { // does it return null?
            return -1;
        }

        return plugin->nativeDescriptor->PortCount;
    }

    EXPORTED int getPluginDescription(LadspaGetPluginDescriptionRequest* request) {
        LadspaPluginDescriptor* plugin = loadedLadspaPlugins[request->index];

        if (!plugin) { // does it return null?
            return -1;
        }
         

        request->name = (char**)plugin->nativeDescriptor->Name;
        request->label = (char**)plugin->nativeDescriptor->Label;
        request->parameterCount = plugin->nativeDescriptor->PortCount;
        request->parameterTypes = (int*) plugin->nativeDescriptor->PortDescriptors;
        request->parameterNames = (char**) plugin->nativeDescriptor->PortNames;
        
        int channelCount = 0;
        if (plugin->inputLeftIndex != -1) {
            ++channelCount;
        }
        if (plugin->inputRightIndex != -1) {
            ++channelCount;
        }
        request->channelCount = channelCount;
        
        for (int i = 0; i < request->parameterCount; ++i) {
            request->parameterLowerValues[i] = plugin->nativeDescriptor->PortRangeHints[i].LowerBound;
            request->parameterUpperValues[i] = plugin->nativeDescriptor->PortRangeHints[i].UpperBound;
        }
    }


    EXPORTED int instantiatePlugin(int pluginId, int sampleRate) {
        LadspaPluginDescriptor* plugin = loadedLadspaPlugins[pluginId];

        if (!plugin) { // does it return null?
            return -1;
        }

        LADSPA_Handle handle = plugin->nativeDescriptor->instantiate(plugin->nativeDescriptor, sampleRate);

        if (plugin->nativeDescriptor->activate) {
            plugin->nativeDescriptor->activate(handle);
        }

        LadspaInstance* instance = new LadspaInstance();
        instance->handle = handle;
        instance->descriptor = plugin;

        int index = globalLadspaInstanceCount++;

        instantiatedLadspaPlugins[index] = instance;

        return index;
    }

    struct LadspaParameter {
        int index;
        float value;
    };

    struct LadspaRenderRequest {
        int instanceId;
        int sampleCount;

        int numberOfParametersDefined;
        LadspaParameter* parameters;

        float* inputLeft;
        float* inputRight;

        float* outputLeft;
        float* outputRight;
    };

    EXPORTED int render(LadspaRenderRequest* request) {
        LOG("Rendering ");
        LadspaInstance* instance = instantiatedLadspaPlugins[request->instanceId];

        if (!instance) { // does it return null?
            return -1;
        }

        LADSPA_Descriptor* nativeDescriptor = instance->descriptor->nativeDescriptor;

        for (int i = 0; i < request->numberOfParametersDefined; ++i) {
            nativeDescriptor->connect_port(instance->handle, request->parameters[i].index, &request->parameters[i].value);
        }
        if (instance->descriptor->inputLeftIndex != -1) {
            nativeDescriptor->connect_port(instance->handle, instance->descriptor->inputLeftIndex, request->inputLeft);
        }
        if (instance->descriptor->inputRightIndex != -1) {
            nativeDescriptor->connect_port(instance->handle, instance->descriptor->inputRightIndex, request->inputRight);
        }
        if (instance->descriptor->outputLeftIndex != -1) {
            nativeDescriptor->connect_port(instance->handle, instance->descriptor->outputLeftIndex, request->outputLeft);
        }
        if (instance->descriptor->outputRightIndex != -1) {
            nativeDescriptor->connect_port(instance->handle, instance->descriptor->outputRightIndex, request->outputRight);
        }

        nativeDescriptor->run(instance->handle, request->sampleCount);
    }

    EXPORTED int deleteInstance(int instanceId) {
        LadspaInstance* plugin = instantiatedLadspaPlugins[instanceId];

        if (!plugin) { // does it return null?
            return -1;
        }

        LADSPA_Descriptor* nativeDescriptor = plugin->descriptor->nativeDescriptor;

        if (nativeDescriptor->deactivate) {
            nativeDescriptor->deactivate(plugin->handle);
        }

        nativeDescriptor->cleanup(plugin->handle);

        return 0;
    }

    EXPORTED int cleanup() {
        // TODO: delete and unload
    }


#ifdef DEBUG

int main() {
    LadspaLoadPluginRequest* lpr = new LadspaLoadPluginRequest();
    lpr->file = "/usr/local/lib/ladspa/vynil_1905.so";
    
    int id = loadPlugin(lpr);

    int paramNumber = getParameterNumber(id);

    LadspaGetPluginDescriptionRequest* lgp = new LadspaGetPluginDescriptionRequest();
    lgp->index = id;
    lgp->parameterLowerValues = new double[paramNumber];
    lgp->parameterUpperValues = new double[paramNumber];

    getPluginDescription(lgp);

    int instanceId = instantiatePlugin(id, 44100);

    LadspaRenderRequest* rr = new LadspaRenderRequest();
    rr->inputLeft = new float[8820];
    rr->inputRight = new float[8820];
    rr->outputLeft = new float[8820];
    rr->outputRight = new float[8820];
    rr->sampleCount = 8820;

    rr->parameters = new LadspaParameter[paramNumber];

    int index = 0;
    for (int i = 0; i < paramNumber; ++i) {
        if (!LADSPA_IS_PORT_AUDIO(lgp->parameterTypes[i])) {
            rr->parameters[index].index = i;
            rr->parameters[index].value = (float)lgp->parameterLowerValues[i];
            ++index;
        }
    }
    rr->numberOfParametersDefined = index;

    render(rr);

}

#endif

}