package com.twitter.elephantbird.pig.load;

import java.io.IOException;

import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.mapreduce.InputFormat;
import org.apache.hadoop.mapreduce.Job;
import org.apache.pig.ResourceSchema;
import org.apache.pig.data.Tuple;
import org.apache.pig.impl.logicalLayer.FrontendException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.Message;
import com.twitter.elephantbird.mapreduce.input.LzoProtobufB64LineInputFormat;
import com.twitter.elephantbird.mapreduce.input.LzoRecordReader;
import com.twitter.elephantbird.mapreduce.io.ProtobufWritable;
import com.twitter.elephantbird.pig.util.PigUtil;
import com.twitter.elephantbird.pig.util.ProjectedProtobufTupleFactory;
import com.twitter.elephantbird.pig.util.ProtobufToPig;
import com.twitter.elephantbird.util.Protobufs;
import com.twitter.elephantbird.util.TypeRef;

/**
 * This is the base class for all base64 encoded, line-oriented protocol buffer based pig loaders.
 * Data is expected to be one base64 encoded serialized protocol buffer per line. The specific
 * protocol buffer is a template parameter.<br>
 * Initialize with a String argument that represents the full classpath of the protocol buffer class to be loaded.<br>
 * The no-arg constructor will not work and is only there for internal Pig reasons.
 */
public class LzoProtobufB64LinePigLoader<M extends Message> extends LzoBaseLoadFunc {
  private static final Logger LOG = LoggerFactory.getLogger(LzoProtobufB64LinePigLoader.class);

  protected TypeRef<M> typeRef = null;
  private final ProtobufToPig protoToPig = new ProtobufToPig();
  private ProjectedProtobufTupleFactory<M> tupleTemplate = null;

  public LzoProtobufB64LinePigLoader() {
  }

  /**
  *
  * @param protoClassName full classpath to the generated Protocol Buffer to be loaded.
  */
  public LzoProtobufB64LinePigLoader(String protoClassName) {
    TypeRef<M> typeRef = PigUtil.getProtobufTypeRef(protoClassName);
    setTypeRef(typeRef);
  }

  /**
   * Set the type parameter so it doesn't get erased by Java.  Must be called before getNext!
   *
   * @param typeRef
   */
  public void setTypeRef(TypeRef<M> typeRef) {
    this.typeRef = typeRef;
  }

  @Override
  public RequiredFieldResponse pushProjection(RequiredFieldList requiredFieldList)
                                              throws FrontendException {
    return pushProjectionHelper(requiredFieldList);
  }

  /**
   * Return every non-null line as a single-element tuple to Pig.
   * <p>
   * A small fraction of bad records in input are tolerated.
   * See  {@link LzoRecordReader} for more information on error handling.
   */
  @Override
  public Tuple getNext() throws IOException {
    if (tupleTemplate == null) {
      tupleTemplate = new ProjectedProtobufTupleFactory<M>(typeRef, requiredFieldList);
    }

    M value = getNextBinaryValue(typeRef);
    return value != null ?
        tupleTemplate.newTuple(value) : null;
  }

  @Override
  public ResourceSchema getSchema(String filename, Job job) throws IOException {
    return new ResourceSchema(protoToPig.toSchema(Protobufs.getMessageDescriptor(typeRef.getRawClass())));
  }

  @Override
  public InputFormat<LongWritable, ProtobufWritable<M>> getInputFormat() throws IOException {
    if (typeRef == null) {
      LOG.error("Protobuf class must be specified before an InputFormat can be created. Do not use the no-argument constructor.");
      throw new IllegalArgumentException("Protobuf class must be specified before an InputFormat can be created. Do not use the no-argument constructor.");
    }
    return new LzoProtobufB64LineInputFormat<M>(typeRef);
  }
}
