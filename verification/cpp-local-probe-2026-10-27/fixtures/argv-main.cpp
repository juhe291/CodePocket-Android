#include <iostream>
#include <string>
int main(int argc, char** argv) {
    if (argc != 1 || std::string(argv[0]) != "program") return 3;
    std::cout << argc << ':' << argv[0] << '\n';
    return 0;
}
