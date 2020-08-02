g++ -DDEBUG_LOGGING=true -DDEBUG=true -w ladspaLoader.cpp -ldl -g
g++ -DDEBUG_LOGGING=true -w -shared -fPIC -Wl,-soname,libladspaplugin.so -o libladspaplugin.so ladspaLoader.cpp -ldl -g

g++ -DDEBUG_LOGGING=true -w -shared -fPIC -Wl,-soname,liblv2plugin.so -o liblv2plugin.so lv2plugin.cpp -ldl -g
