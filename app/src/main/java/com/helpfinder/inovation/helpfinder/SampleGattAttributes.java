/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.helpfinder.inovation.helpfinder;

import java.util.HashMap;

/**
 * This class includes a small subset of standard GATT attributes for demonstration purposes.
 */
public class SampleGattAttributes {
    public static String HELPFINDER_SERVICE = "00431c4a-a7a4-428b-a96d-d92d43c8c7cf";
    public static String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";
    private static HashMap<String, String> attributes = new HashMap();

    static {
        attributes.put(HELPFINDER_SERVICE, "HELPFINDER_SERVICE");
        // Sample Services.
        attributes.put("0000180d-0000-1000-8000-00805f9b34fb", "HELPFINDER_SERVICE");
        attributes.put("0000180a-0000-1000-8000-00805f9b34fb", "Device Information Service");

        // Sample Characteristics.
        attributes.put("00002a29-0000-1000-8000-00805f9b34fb", "Manufacturer Name String");
    }

    public static String lookup(String uuid, String defaultName) {
        String name = attributes.get(uuid);
        return name == null ? defaultName : name;
    }
}
