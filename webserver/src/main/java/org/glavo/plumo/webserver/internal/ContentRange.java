/*
 * Copyright 2023 Glavo
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
package org.glavo.plumo.webserver.internal;

public final class ContentRange {
    private static ContentRange parseRange(String range) {
        if (range.length() < 2) {
            return null;
        }

        int idx = range.indexOf('-');
        if (idx < 0) {
            return null;
        }

        long start;
        long end;

        if (idx == 0) {
            start = -1;
        } else {
            try {
                start = Long.parseLong(range.substring(0, idx));
                assert start >= 0;
            } catch (NumberFormatException e) {
                return null;
            }
        }

        if (idx == range.length() - 1) {
            end = -1;
        } else {
            try {
                end = Long.parseLong(range.substring(idx + 1));
                if (end < 0) {
                    return null;
                }
            } catch (NumberFormatException e) {
                return null;
            }
        }

        return new ContentRange(start, end);
    }

    public static ContentRange[] parseRanges(String rangeHeader) {
        int idx = rangeHeader.indexOf('=');
        if (idx < 0 || idx == rangeHeader.length() - 1) {
            return null;
        }

        String unit = rangeHeader.substring(0, idx).trim();
        if (!unit.equalsIgnoreCase("bytes")) {
            // Do we need to implement it for other units?
            return null;
        }

        if (rangeHeader.indexOf(',', idx + 1) < 0) {
            ContentRange r = parseRange(rangeHeader.substring(idx + 1));
            if (r != null) {
                return new ContentRange[]{r};
            } else {
                return null;
            }
        }

        String[] ranges = rangeHeader.substring(idx + 1).split(",");
        ContentRange[] res = new ContentRange[ranges.length];

        for (int i = 0; i < ranges.length; i++) {
            ContentRange r = parseRange(ranges[i].trim());
            if (r == null) {
                return null;
            }

            res[i] = r;
        }

        return res;
    }

    public long start;
    public long end;

    private ContentRange(long start, long end) {
        this.start = start;
        this.end = end;
    }

    @Override
    public String toString() {
        return super.toString();
    }
}
