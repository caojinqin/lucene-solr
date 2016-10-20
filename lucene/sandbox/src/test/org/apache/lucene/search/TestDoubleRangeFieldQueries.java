/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.lucene.search;

import java.util.Arrays;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoubleRangeField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.RandomIndexWriter;
import org.apache.lucene.store.Directory;

/**
 * Random testing for RangeFieldQueries.
 */
public class TestDoubleRangeFieldQueries extends BaseRangeFieldQueryTestCase {
  private static final String FIELD_NAME = "doubleRangeField";

  private double nextDoubleInternal() {
    if (rarely()) {
      return random().nextBoolean() ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
    }
    double max = Double.MAX_VALUE / 2;
    return (max + max) * random().nextDouble() - max;
  }

  @Override
  protected Range nextRange(int dimensions) {
    double[] min = new double[dimensions];
    double[] max = new double[dimensions];

    double minV, maxV;
    for (int d=0; d<dimensions; ++d) {
      minV = nextDoubleInternal();
      maxV = nextDoubleInternal();
      min[d] = Math.min(minV, maxV);
      max[d] = Math.max(minV, maxV);
    }
    return new DoubleRange(min, max);
  }

  @Override
  protected DoubleRangeField newRangeField(Range r) {
    return new DoubleRangeField(FIELD_NAME, ((DoubleRange)r).min, ((DoubleRange)r).max);
  }

  @Override
  protected Query newIntersectsQuery(Range r) {
    return DoubleRangeField.newIntersectsQuery(FIELD_NAME, ((DoubleRange)r).min, ((DoubleRange)r).max);
  }

  @Override
  protected Query newContainsQuery(Range r) {
    return DoubleRangeField.newContainsQuery(FIELD_NAME, ((DoubleRange)r).min, ((DoubleRange)r).max);
  }

  @Override
  protected Query newWithinQuery(Range r) {
    return DoubleRangeField.newWithinQuery(FIELD_NAME, ((DoubleRange)r).min, ((DoubleRange)r).max);
  }

  /** Basic test */
  public void testBasics() throws Exception {
    Directory dir = newDirectory();
    RandomIndexWriter writer = new RandomIndexWriter(random(), dir);

    // intersects (within)
    Document document = new Document();
    document.add(new DoubleRangeField(FIELD_NAME, new double[] {-10.0, -10.0}, new double[] {9.1, 10.1}));
    writer.addDocument(document);

    // intersects (crosses)
    document = new Document();
    document.add(new DoubleRangeField(FIELD_NAME, new double[] {10.0, -10.0}, new double[] {20.0, 10.0}));
    writer.addDocument(document);

    // intersects (contains)
    document = new Document();
    document.add(new DoubleRangeField(FIELD_NAME, new double[] {-20.0, -20.0}, new double[] {30.0, 30.1}));
    writer.addDocument(document);

    // intersects (crosses)
    document = new Document();
    document.add(new DoubleRangeField(FIELD_NAME, new double[] {-11.1, -11.2}, new double[] {1.23, 11.5}));
    writer.addDocument(document);

    // intersects (crosses)
    document = new Document();
    document.add(new DoubleRangeField(FIELD_NAME, new double[] {12.33, 1.2}, new double[] {15.1, 29.9}));
    writer.addDocument(document);

    // disjoint
    document = new Document();
    document.add(new DoubleRangeField(FIELD_NAME, new double[] {-122.33, 1.2}, new double[] {-115.1, 29.9}));
    writer.addDocument(document);

    // intersects (crosses)
    document = new Document();
    document.add(new DoubleRangeField(FIELD_NAME, new double[] {Double.NEGATIVE_INFINITY, 1.2}, new double[] {-11.0, 29.9}));
    writer.addDocument(document);

    // equal (within, contains, intersects)
    document = new Document();
    document.add(new DoubleRangeField(FIELD_NAME, new double[] {-11, -15}, new double[] {15, 20}));
    writer.addDocument(document);

    // search
    IndexReader reader = writer.getReader();
    IndexSearcher searcher = newSearcher(reader);
    assertEquals(7, searcher.count(DoubleRangeField.newIntersectsQuery(FIELD_NAME,
        new double[] {-11.0, -15.0}, new double[] {15.0, 20.0})));
    assertEquals(2, searcher.count(DoubleRangeField.newWithinQuery(FIELD_NAME,
        new double[] {-11.0, -15.0}, new double[] {15.0, 20.0})));
    assertEquals(2, searcher.count(DoubleRangeField.newContainsQuery(FIELD_NAME,
        new double[] {-11.0, -15.0}, new double[] {15.0, 20.0})));

    reader.close();
    writer.close();
    dir.close();
  }

  /** DoubleRange test class implementation - use to validate DoubleRangeField */
  private class DoubleRange extends Range {
    double[] min;
    double[] max;

    DoubleRange(double[] min, double[] max) {
      assert min != null && max != null && min.length > 0 && max.length > 0
          : "test box: min/max cannot be null or empty";
      assert min.length == max.length : "test box: min/max length do not agree";
      this.min = min;
      this.max = max;
    }

    @Override
    protected int numDimensions() {
      return min.length;
    }

    @Override
    protected Double getMin(int dim) {
      return min[dim];
    }

    @Override
    protected void setMin(int dim, Object val) {
      double v = (Double)val;
      if (min[dim] < v) {
        max[dim] = v;
      } else {
        min[dim] = v;
      }
    }

    @Override
    protected Double getMax(int dim) {
      return max[dim];
    }

    @Override
    protected void setMax(int dim, Object val) {
      double v = (Double)val;
      if (max[dim] > v) {
        min[dim] = v;
      } else {
        max[dim] = v;
      }
    }

    @Override
    protected boolean isEqual(Range other) {
      DoubleRange o = (DoubleRange)other;
      return Arrays.equals(min, o.min) && Arrays.equals(max, o.max);
    }

    @Override
    protected boolean isDisjoint(Range o) {
      DoubleRange other = (DoubleRange)o;
      for (int d=0; d<this.min.length; ++d) {
        if (this.min[d] > other.max[d] || this.max[d] < other.min[d]) {
          // disjoint:
          return true;
        }
      }
      return false;
    }

    @Override
    protected boolean isWithin(Range o) {
      DoubleRange other = (DoubleRange)o;
      for (int d=0; d<this.min.length; ++d) {
        if ((this.min[d] >= other.min[d] && this.max[d] <= other.max[d]) == false) {
          // not within:
          return false;
        }
      }
      return true;
    }

    @Override
    protected boolean contains(Range o) {
      DoubleRange other = (DoubleRange) o;
      for (int d=0; d<this.min.length; ++d) {
        if ((this.min[d] <= other.min[d] && this.max[d] >= other.max[d]) == false) {
          // not contains:
          return false;
        }
      }
      return true;
    }

    @Override
    public String toString() {
      StringBuilder b = new StringBuilder();
      b.append("Box(");
      b.append(min[0]);
      b.append(" TO ");
      b.append(max[0]);
      for (int d=1; d<min.length; ++d) {
        b.append(", ");
        b.append(min[d]);
        b.append(" TO ");
        b.append(max[d]);
      }
      b.append(")");

      return b.toString();
    }
  }
}
