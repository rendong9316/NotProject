#pragma once

#include <map>
#include <string>
#include <vector>

class Json {
public:
    enum class Type { Null, Boolean, Number, String, Array, Object };

    Json();
    explicit Json(bool value);
    explicit Json(double value);
    explicit Json(const std::string& value);

    static Json Array();
    static Json Object();
    static bool Parse(const std::string& source, Json& value, std::string& error);

    Type type() const { return type_; }
    bool isNull() const { return type_ == Type::Null; }
    bool isBoolean() const { return type_ == Type::Boolean; }
    bool isNumber() const { return type_ == Type::Number; }
    bool isString() const { return type_ == Type::String; }
    bool isArray() const { return type_ == Type::Array; }
    bool isObject() const { return type_ == Type::Object; }

    bool boolean(bool fallback = false) const;
    int integer(int fallback = 0) const;
    const std::string& string() const;
    const std::vector<Json>& array() const;
    std::vector<Json>& array();
    const std::map<std::string, Json>& object() const;
    std::map<std::string, Json>& object();

    const Json& get(const std::string& key) const;
    Json& operator[](const std::string& key);
    void push(const Json& value);
    std::string Serialize(int indent = 0) const;

private:
    Type type_;
    bool boolean_;
    double number_;
    std::string string_;
    std::vector<Json> array_;
    std::map<std::string, Json> object_;
};
