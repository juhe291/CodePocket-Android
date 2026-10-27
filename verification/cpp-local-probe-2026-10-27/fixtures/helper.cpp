#include "model.hpp"
#include <algorithm>
#include <map>
#include <queue>
#include <sstream>
std::vector<int> sorted_values(int first, int second) {
    std::vector<int> values{second, first};
    std::sort(values.begin(), values.end());
    return values;
}
std::string describe(const std::vector<int>& values) {
    std::map<int, int> counts;
    std::queue<int> pending;
    for (int value : values) { ++counts[value]; pending.push(value); }
    std::ostringstream out;
    out << pending.front() << ':' << values.back() << ':' << counts.size();
    return out.str();
}
