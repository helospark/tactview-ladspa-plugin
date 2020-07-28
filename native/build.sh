g++ -DDEBUG_LOGGING=true -DDEBUG=true -w *.cpp -ldl -g
g++ -DDEBUG_LOGGING=true -w -shared -fPIC -Wl,-soname,libladspaplugin.so -o libladspaplugin.so *.cpp -ldl -g
