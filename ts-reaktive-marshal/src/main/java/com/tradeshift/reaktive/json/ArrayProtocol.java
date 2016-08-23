package com.tradeshift.reaktive.json;

import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javaslang.control.Try;

public class ArrayProtocol<E> {
    private static final Logger log = LoggerFactory.getLogger(ArrayProtocol.class);
    
    public static <E> JSONReadProtocol<E> read(JSONReadProtocol<E> innerProtocol) {
        return new JSONReadProtocol<E>() {
            private final JSONReadProtocol<E> owner = this;
            
            @Override
            public com.tradeshift.reaktive.json.JSONReadProtocol.Reader<E> reader() {
                return new Reader<E>() {
                    private final Reader<E> inner = innerProtocol.reader();
                    private int nestedObjects = 0;
                    private boolean matched = false;
                    private boolean wasEmpty = true;

                    @Override
                    public Try<E> reset() {
                        nestedObjects = 0;
                        matched = false;  
                        wasEmpty = true;
                        return inner.reset();
                    }

                    @Override
                    public Try<E> apply(JSONEvent evt) {
                        if (nestedObjects == 0) {
                            if (evt == JSONEvent.START_OBJECT) {
                                nestedObjects++;
                                return none();
                            } else if (evt == JSONEvent.START_ARRAY) {
                                log.debug("Array has started: {}", owner);
                                matched = true;
                                nestedObjects++;
                                return none();
                            } else { // literal, just skip
                                return none();
                            }
                        } else if (matched && evt == JSONEvent.END_ARRAY && nestedObjects == 1) {
                            log.debug("Array has ended: {}", owner);
                            Try<E> result = reset();
                            return (wasEmpty && isNone(result)) ? innerProtocol.empty() : result;
                        } else {
                            Try<E> result = none();
                            
                            if (matched) {
                                log.debug("Array forwarding {} at level {}", evt, nestedObjects);
                                wasEmpty = false;
                                result = inner.apply(evt);
                            }
                            
                            if (evt == JSONEvent.END_ARRAY || evt == JSONEvent.END_OBJECT) {
                                log.debug("    (nested--) {} on {}", nestedObjects, owner);
                                nestedObjects--;
                            } else if (evt == JSONEvent.START_ARRAY || evt == JSONEvent.START_OBJECT) {
                                log.debug("    (nested++) {} on {}", nestedObjects, owner);
                                nestedObjects++;
                            }
                            
                            return result;
                        }
                    }
                };
            }
            
            @Override
            protected Try<E> empty() {
                return innerProtocol.empty();
            }
            
            @Override
            public String toString() {
                return "[ " + innerProtocol + "]";
            }
        };
    }

    public static <E> JSONWriteProtocol<E> write(JSONWriteProtocol<E> innerProtocol) {
        return new JSONWriteProtocol<E>() {
            private final Writer<E> writer = e -> {
                Writer<E> inner = innerProtocol.writer();
                return
                    Stream.concat(
                        Stream.concat(
                            Stream.of(JSONEvent.START_ARRAY),
                            inner.apply(e)
                        ),
                        Stream.of(JSONEvent.END_ARRAY)
                    );
                    
            };
            
            @Override
            public JSONWriteProtocol.Writer<E> writer() {
                return writer;
            }
            
            @Override
            public boolean isEmpty(E value) {
                return innerProtocol.isEmpty(value);
            }
            
            @Override
            public String toString() {
                return "[ " + innerProtocol + "]";
            }            
        };
    }
    
    public static <E> JSONProtocol<E> readWrite(JSONProtocol<E> innerProtocol) {
        JSONReadProtocol<E> read = read(innerProtocol);
        JSONWriteProtocol<E> write = write(innerProtocol);
        return new JSONProtocol<E>() {
            @Override
            public Writer<E> writer() {
                return write.writer();
            }

            @Override
            public Reader<E> reader() {
                return read.reader();
            }
            
            @Override
            public boolean isEmpty(E value) {
                return write.isEmpty(value);
            }
            
            @Override
            protected Try<E> empty() {
                return read.empty();
            }
            
            @Override
            public String toString() {
                return "[ " + innerProtocol + "]";
            }            
        };
    }
}
