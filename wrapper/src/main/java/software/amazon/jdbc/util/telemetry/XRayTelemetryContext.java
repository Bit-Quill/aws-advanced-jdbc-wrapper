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

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.entities.Segment;
import com.amazonaws.xray.entities.Subsegment;

public class XRayTelemetryContext implements TelemetryContext {

  private boolean isSubsegment;

  private Segment segment;
  private Subsegment subsegment;

  private String name;

  public XRayTelemetryContext(String name) {
    this.name = name;
    isSubsegment = AWSXRay.getTraceEntity() != null;
    if (isSubsegment) {
      subsegment = AWSXRay.beginSubsegment(name);
    } else {
      segment = AWSXRay.beginSegment(name);
    }
  }

  @Override
  public void setSuccess(boolean success) {
    if (isSubsegment) {
      subsegment.setError(!success);
    } else {
      segment.setError(!success);
    }
  }

  @Override
  public void setAttribute(String key, String value) {
    if (isSubsegment) {
      subsegment.putAnnotation(key, value);
    } else {
      segment.putAnnotation(key, value);
    }
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String toString() {
    if (isSubsegment) {
      return subsegment.getId();
    } else {
      return segment.getId();
    }
  }

  @Override
  public void close() {
    if (isSubsegment) {
      if (subsegment != null) {
        subsegment.close();
        subsegment = null;
      }
    } else {
      if (segment != null) {
        segment.close();
        segment = null;
      }
    }
  }
}
