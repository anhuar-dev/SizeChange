package dev.anhuar.sizeChange.util;

/*
 * ========================================================
 * SizeChange - GsonUtil.java
 *
 * @author Anhuar Ruiz | Anhuar Dev | myclass
 * @web https://anhuar.dev
 * @date 22/06/2025
 *
 * License: MIT License - See LICENSE file for details.
 * Copyright (c) 2025 Anhuar Dev. All rights reserved.
 * ========================================================
 */

import com.google.gson.Gson;
import lombok.Getter;
import lombok.experimental.UtilityClass;
import org.bson.json.JsonMode;
import org.bson.json.JsonWriterSettings;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@UtilityClass
public class GsonUtil {

    @Getter
    private final Gson gson = new Gson();

    @Getter
    private final JsonWriterSettings writerSettings = JsonWriterSettings.builder().outputMode(JsonMode.RELAXED).build();

    private final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneId.from(ZoneOffset.UTC));

    public String getDateFromLong(long currentMilliseconds) {
        return DATE_TIME_FORMATTER.format(Instant.ofEpochMilli(currentMilliseconds));
    }

    public <T> T parseJsonString(String jsonString, Class<T> classOfT) {
        try {
            return getGson().fromJson(jsonString, classOfT);
        } catch (Exception e) {
            throw new RuntimeException("Error al parsear JSON: " + e.getMessage(), e);
        }
    }
}