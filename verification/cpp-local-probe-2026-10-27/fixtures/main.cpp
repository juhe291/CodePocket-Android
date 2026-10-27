#include "model.hpp"
#include <iostream>
#include <stdexcept>
struct Lifetime {
    Lifetime() { std::cout << "ready\n"; }
    ~Lifetime() { std::cout << "bye\n"; }
} lifetime;
thread_local int selected = 0;
int main() {
    int first = 0, second = 0;
    if (!(std::cin >> first >> second)) return 2;
    selected = first;
    std::cout << describe(sorted_values(selected, second)) << '\n';
    try { throw std::runtime_error("caught"); }
    catch (const std::exception& error) { std::cout << error.what() << '\n'; }
    return 0;
}
