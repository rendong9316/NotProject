#include "json.h"

#include <cmath>
#include <cstdio>
#include <cstdlib>

namespace {

const Json kNull;

void AppendUtf8(unsigned int codepoint, std::string& output) {
    if (codepoint <= 0x7f) {
        output.push_back(static_cast<char>(codepoint));
    } else if (codepoint <= 0x7ff) {
        output.push_back(static_cast<char>(0xc0 | (codepoint >> 6)));
        output.push_back(static_cast<char>(0x80 | (codepoint & 0x3f)));
    } else if (codepoint <= 0xffff) {
        output.push_back(static_cast<char>(0xe0 | (codepoint >> 12)));
        output.push_back(static_cast<char>(0x80 | ((codepoint >> 6) & 0x3f)));
        output.push_back(static_cast<char>(0x80 | (codepoint & 0x3f)));
    } else {
        output.push_back(static_cast<char>(0xf0 | (codepoint >> 18)));
        output.push_back(static_cast<char>(0x80 | ((codepoint >> 12) & 0x3f)));
        output.push_back(static_cast<char>(0x80 | ((codepoint >> 6) & 0x3f)));
        output.push_back(static_cast<char>(0x80 | (codepoint & 0x3f)));
    }
}

class Parser {
public:
    explicit Parser(const std::string& source) : source_(source), position_(0) {}

    bool ParseValue(Json& value, std::string& error) {
        SkipWhitespace();
        if (position_ >= source_.size()) return Fail("unexpected end of input", error);
        const char current = source_[position_];
        if (current == 'n') return ParseLiteral("null", Json(), value, error);
        if (current == 't') return ParseLiteral("true", Json(true), value, error);
        if (current == 'f') return ParseLiteral("false", Json(false), value, error);
        if (current == '"') {
            std::string text;
            if (!ParseString(text, error)) return false;
            value = Json(text);
            return true;
        }
        if (current == '[') return ParseArray(value, error);
        if (current == '{') return ParseObject(value, error);
        if (current == '-' || (current >= '0' && current <= '9')) return ParseNumber(value, error);
        return Fail("unexpected token", error);
    }

    bool Finished(std::string& error) {
        SkipWhitespace();
        return position_ == source_.size() || Fail("trailing content", error);
    }

private:
    void SkipWhitespace() {
        while (position_ < source_.size()) {
            const char c = source_[position_];
            if (c != ' ' && c != '\t' && c != '\r' && c != '\n') break;
            ++position_;
        }
    }

    bool Fail(const char* message, std::string& error) {
        char buffer[160];
        std::snprintf(buffer, sizeof(buffer), "%s at byte %zu", message, position_);
        error = buffer;
        return false;
    }

    bool ParseLiteral(const char* literal, const Json& literalValue, Json& value, std::string& error) {
        const std::string expected(literal);
        if (source_.compare(position_, expected.size(), expected) != 0) return Fail("invalid literal", error);
        position_ += expected.size();
        value = literalValue;
        return true;
    }

    bool ParseHex4(unsigned int& value, std::string& error) {
        if (position_ + 4 > source_.size()) return Fail("incomplete unicode escape", error);
        value = 0;
        for (int i = 0; i < 4; ++i) {
            const char c = source_[position_++];
            value <<= 4;
            if (c >= '0' && c <= '9') value |= static_cast<unsigned int>(c - '0');
            else if (c >= 'a' && c <= 'f') value |= static_cast<unsigned int>(c - 'a' + 10);
            else if (c >= 'A' && c <= 'F') value |= static_cast<unsigned int>(c - 'A' + 10);
            else return Fail("invalid unicode escape", error);
        }
        return true;
    }

    bool ParseString(std::string& value, std::string& error) {
        if (source_[position_++] != '"') return Fail("expected string", error);
        value.clear();
        while (position_ < source_.size()) {
            const unsigned char c = static_cast<unsigned char>(source_[position_++]);
            if (c == '"') return true;
            if (c < 0x20) return Fail("control character in string", error);
            if (c != '\\') {
                value.push_back(static_cast<char>(c));
                continue;
            }
            if (position_ >= source_.size()) return Fail("incomplete escape", error);
            const char escape = source_[position_++];
            switch (escape) {
            case '"': value.push_back('"'); break;
            case '\\': value.push_back('\\'); break;
            case '/': value.push_back('/'); break;
            case 'b': value.push_back('\b'); break;
            case 'f': value.push_back('\f'); break;
            case 'n': value.push_back('\n'); break;
            case 'r': value.push_back('\r'); break;
            case 't': value.push_back('\t'); break;
            case 'u': {
                unsigned int codepoint = 0;
                if (!ParseHex4(codepoint, error)) return false;
                if (codepoint >= 0xd800 && codepoint <= 0xdbff && position_ + 6 <= source_.size() &&
                    source_[position_] == '\\' && source_[position_ + 1] == 'u') {
                    position_ += 2;
                    unsigned int low = 0;
                    if (!ParseHex4(low, error)) return false;
                    if (low >= 0xdc00 && low <= 0xdfff)
                        codepoint = 0x10000 + ((codepoint - 0xd800) << 10) + (low - 0xdc00);
                }
                AppendUtf8(codepoint, value);
                break;
            }
            default: return Fail("invalid escape", error);
            }
        }
        return Fail("unterminated string", error);
    }

    bool ParseNumber(Json& value, std::string& error) {
        const size_t start = position_;
        if (source_[position_] == '-') ++position_;
        if (position_ >= source_.size()) return Fail("invalid number", error);
        if (source_[position_] == '0') ++position_;
        else {
            if (source_[position_] < '1' || source_[position_] > '9') return Fail("invalid number", error);
            while (position_ < source_.size() && source_[position_] >= '0' && source_[position_] <= '9') ++position_;
        }
        if (position_ < source_.size() && source_[position_] == '.') {
            ++position_;
            if (position_ >= source_.size() || source_[position_] < '0' || source_[position_] > '9')
                return Fail("invalid fraction", error);
            while (position_ < source_.size() && source_[position_] >= '0' && source_[position_] <= '9') ++position_;
        }
        if (position_ < source_.size() && (source_[position_] == 'e' || source_[position_] == 'E')) {
            ++position_;
            if (position_ < source_.size() && (source_[position_] == '+' || source_[position_] == '-')) ++position_;
            if (position_ >= source_.size() || source_[position_] < '0' || source_[position_] > '9')
                return Fail("invalid exponent", error);
            while (position_ < source_.size() && source_[position_] >= '0' && source_[position_] <= '9') ++position_;
        }
        value = Json(std::strtod(source_.substr(start, position_ - start).c_str(), nullptr));
        return true;
    }

    bool ParseArray(Json& value, std::string& error) {
        ++position_;
        value = Json::Array();
        SkipWhitespace();
        if (position_ < source_.size() && source_[position_] == ']') { ++position_; return true; }
        while (true) {
            Json item;
            if (!ParseValue(item, error)) return false;
            value.push(item);
            SkipWhitespace();
            if (position_ >= source_.size()) return Fail("unterminated array", error);
            if (source_[position_] == ']') { ++position_; return true; }
            if (source_[position_++] != ',') return Fail("expected comma", error);
        }
    }

    bool ParseObject(Json& value, std::string& error) {
        ++position_;
        value = Json::Object();
        SkipWhitespace();
        if (position_ < source_.size() && source_[position_] == '}') { ++position_; return true; }
        while (true) {
            SkipWhitespace();
            if (position_ >= source_.size() || source_[position_] != '"') return Fail("expected object key", error);
            std::string key;
            if (!ParseString(key, error)) return false;
            SkipWhitespace();
            if (position_ >= source_.size() || source_[position_++] != ':') return Fail("expected colon", error);
            Json item;
            if (!ParseValue(item, error)) return false;
            value.object()[key] = item;
            SkipWhitespace();
            if (position_ >= source_.size()) return Fail("unterminated object", error);
            if (source_[position_] == '}') { ++position_; return true; }
            if (source_[position_++] != ',') return Fail("expected comma", error);
        }
    }

    const std::string& source_;
    size_t position_;
};

std::string Escape(const std::string& value) {
    std::string output = "\"";
    char buffer[8];
    for (unsigned char c : value) {
        switch (c) {
        case '"': output += "\\\""; break;
        case '\\': output += "\\\\"; break;
        case '\b': output += "\\b"; break;
        case '\f': output += "\\f"; break;
        case '\n': output += "\\n"; break;
        case '\r': output += "\\r"; break;
        case '\t': output += "\\t"; break;
        default:
            if (c < 0x20) {
                std::snprintf(buffer, sizeof(buffer), "\\u%04x", c);
                output += buffer;
            } else output.push_back(static_cast<char>(c));
        }
    }
    output.push_back('"');
    return output;
}

std::string Spaces(int count) { return std::string(static_cast<size_t>(count), ' '); }

} // namespace

Json::Json() : type_(Type::Null), boolean_(false), number_(0) {}
Json::Json(bool value) : type_(Type::Boolean), boolean_(value), number_(0) {}
Json::Json(int value) : type_(Type::Number), boolean_(false), number_(static_cast<double>(value)) {}
Json::Json(double value) : type_(Type::Number), boolean_(false), number_(value) {}
Json::Json(const char* value) : type_(Type::String), boolean_(false), number_(0), string_(value ? value : "") {}
Json::Json(const std::string& value) : type_(Type::String), boolean_(false), number_(0), string_(value) {}

Json Json::Array() { Json value; value.type_ = Type::Array; return value; }
Json Json::Object() { Json value; value.type_ = Type::Object; return value; }

bool Json::Parse(const std::string& source, Json& value, std::string& error) {
    Parser parser(source);
    return parser.ParseValue(value, error) && parser.Finished(error);
}

bool Json::boolean(bool fallback) const { return isBoolean() ? boolean_ : fallback; }
int Json::integer(int fallback) const { return isNumber() ? static_cast<int>(number_) : fallback; }
double Json::number(double fallback) const { return isNumber() ? number_ : fallback; }
const std::string& Json::string() const { return string_; }
const std::vector<Json>& Json::array() const { return array_; }
std::vector<Json>& Json::array() { return array_; }
const std::map<std::string, Json>& Json::object() const { return object_; }
std::map<std::string, Json>& Json::object() { return object_; }

const Json& Json::get(const std::string& key) const {
    const auto found = object_.find(key);
    return found == object_.end() ? kNull : found->second;
}

Json& Json::operator[](const std::string& key) {
    if (!isObject()) { type_ = Type::Object; object_.clear(); }
    return object_[key];
}

void Json::push(const Json& value) {
    if (!isArray()) { type_ = Type::Array; array_.clear(); }
    array_.push_back(value);
}

std::string Json::Serialize(int indent) const {
    switch (type_) {
    case Type::Null: return "null";
    case Type::Boolean: return boolean_ ? "true" : "false";
    case Type::Number: {
        char buffer[64];
        if (std::floor(number_) == number_) std::snprintf(buffer, sizeof(buffer), "%.0f", number_);
        else std::snprintf(buffer, sizeof(buffer), "%.15g", number_);
        return buffer;
    }
    case Type::String: return Escape(string_);
    case Type::Array: {
        if (array_.empty()) return "[]";
        std::string output = "[\n";
        for (size_t i = 0; i < array_.size(); ++i) {
            output += Spaces(indent + 2) + array_[i].Serialize(indent + 2);
            output += (i + 1 == array_.size()) ? "\n" : ",\n";
        }
        return output + Spaces(indent) + "]";
    }
    case Type::Object: {
        if (object_.empty()) return "{}";
        std::string output = "{\n";
        size_t index = 0;
        for (const auto& pair : object_) {
            output += Spaces(indent + 2) + Escape(pair.first) + ": " + pair.second.Serialize(indent + 2);
            output += (++index == object_.size()) ? "\n" : ",\n";
        }
        return output + Spaces(indent) + "}";
    }
    }
    return "null";
}
