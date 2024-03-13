/*
 * Copyright 2024 Glavo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.glavo.webdav;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class WebDAV {
    private static String getVersion() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                WebDAV.class.getResourceAsStream("version.txt"), StandardCharsets.UTF_8))) {
            return reader.readLine();
        } catch (IOException | NullPointerException e) {
            return "unknown";
        }
    }

    public static void main(String[] args) {
        // TODO
    }
}
