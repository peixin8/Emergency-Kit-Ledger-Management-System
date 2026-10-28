package com.emergency.ledger;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;

/** VLAN 不允许先把 JSON 浮点数截断为整数再做范围校验。 */
public class VlanIntegerDeserializer extends JsonDeserializer<Integer> {
    @Override
    public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if(parser.hasToken(JsonToken.VALUE_NUMBER_FLOAT)) {
            throw context.weirdNumberException(parser.getNumberValue(),Integer.class,"VLAN 必须使用整数，不能使用小数或指数形式");
        }
        return context.readValue(parser,Integer.class);
    }
}
