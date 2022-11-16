/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package software.amazon.jdbc.util.telemetry;

import java.util.Properties;
import software.amazon.jdbc.PropertyDefinition;

public class DefaultTelemetryFactory implements TelemetryFactory {
  private final Properties properties;

  public DefaultTelemetryFactory(Properties properties) {
    this.properties = properties;
  }

  @Override
  public TelemetryContext openTelemetryContext(String name) {
    if (PropertyDefinition.ENABLE_TELEMETRY.getBoolean(properties)) {
      if ("otlp".equalsIgnoreCase(PropertyDefinition.TELEMETRY_TRACES_BACKEND.getString(properties))) {
        return OpenTelemetryFactory.openTelemetryContext(name);
      } else if ("xray".equalsIgnoreCase(PropertyDefinition.TELEMETRY_TRACES_BACKEND.getString(properties))) {
        return XRayTelemetryFactory.openTelemetryContext(name);
      }
    }
    return new NullTelemetryContext(name);
  }

  @Override
  public TelemetryCounter createCounter(String name) {
    if (PropertyDefinition.ENABLE_TELEMETRY.getBoolean(properties)) {
      if ("otlp".equalsIgnoreCase(PropertyDefinition.TELEMETRY_METRICS_BACKEND.getString(properties))) {
        return OpenTelemetryFactory.createCounter(name);
      }
    }
    return new NullTelemetryCounter(name);
  }

  @Override
  public TelemetryGauge createGauge(String name, long measure) {
    if (PropertyDefinition.ENABLE_TELEMETRY.getBoolean(properties)) {
      if ("otlp".equalsIgnoreCase(PropertyDefinition.TELEMETRY_METRICS_BACKEND.getString(properties))) {
        return OpenTelemetryFactory.createGauge(name, measure);
      }
    }
    return new NullTelemetryGauge(name, measure);
  }
}
