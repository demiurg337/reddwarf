/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

/*
 * This file provides declarations for a circular byte-buffer.
 */

#ifndef SGS_BUFFER_H
#define SGS_BUFFER_H  1

/*
 * sgs_buffer typedef (declare before any #includes)
 */
typedef struct sgs_buffer sgs_buffer;

/*
 * INCLUDES
 */
#include <stdint.h>
#include <stdlib.h>

/*
 * STRUCTS
 */
struct sgs_buffer {
  /* Total amount of memory allocated to the "buf" pointer. */
  size_t capacity;
  
  /* Current position of the start of the data in the buffer. */
  size_t position;
  
  /* Number of bytes currently stored in the buffer. */
  size_t size;
  
  /* Array of the actual data. */
  uint8_t *buf;
};

/*
 * FUNCTION DECLARATIONS
 */

/*
 * function: sgs_buffer_capacity()
 *
 * Returns the total capacity of a buffer.
 */
size_t sgs_buffer_capacity(const sgs_buffer *buffer);

/*
 * function: sgs_buffer_new()
 *
 * Allocates a buffer with at least the specified capacity (in bytes).  NULL is
 * returned if allocation fails.
 */
sgs_buffer *sgs_buffer_new(size_t capacity);

/*
 * function: sgs_buffer_free()
 *
 * Safely deallocates a buffer.
 */
void sgs_buffer_free(sgs_buffer *buffer);

/*
 * function: sgs_buffer_empty()
 *
 * Clears all data from the buffer.  This doesnt' actually write anything to the
 * buffer's memory but merely resets the internal state so that the buffer
 * "forgets" about any data currently held.
 */
void sgs_buffer_empty(sgs_buffer *buffer);

/*
 * function: sgs_buffer_peek()
 *
 * Copies len bytes of data out of the buffer into the specified array, but does
 * not update the buffer's internal state (such that subsequent calls to
 * sgs_buffer_peek() or sgs_buffer_write() will reread the same bytes again).
 * Returns 0 on success or -1 if the buffer does not contain enough data to
 * satisfy the request.
 */
int sgs_buffer_peek(const sgs_buffer *buffer, uint8_t *data, size_t len);


/*
 * function: sgs_buffer_read()
 *
 * Copies len bytes of data out of the buffer into the specified array.  Returns
 * 0 on success or -1 if the buffer does not contain enough data to satisfy the
 * request.
 */
int sgs_buffer_read(sgs_buffer *buffer, uint8_t *data, size_t len);

/*
 * function: sgs_buffer_read_from_fd()
 *
 * Copies data from the specified file descriptor into the buffer.  Copying
 * stops if (a) the buffer runs out of room, or (b) a call to read() on the file
 * descriptor returns any value other than the requested length.  TODO - returns?
 */
int sgs_buffer_read_from_fd(sgs_buffer *buffer, int fd);

/*
 * function: sgs_buffer_remaining_capacity()
 *
 * Returns the amount of space remaining in a buffer (capacity - size).  This is
 * NOT necessarily the number of bytes that can be safely written (contiguously)
 * to the tail of the buffer (since the buffer is circular).  For this function,
 * use sgs_buffer_writable_len().
 */
size_t sgs_buffer_remaining_capacity(const sgs_buffer *buffer);

/*
 * function: sgs_buffer_size()
 *
 * Returns the current size of the stored data.
 */
size_t sgs_buffer_size(const sgs_buffer *buffer);

/*
 * function: sgs_buffer_write()
 *
 * Copies data into the buffer as long as the length of the data is less than
 * the remaining capacity of the buffer, returning 0.  Otherwise, returns -1 and
 * errno is set to ENOBUFS.
 */
int sgs_buffer_write(sgs_buffer *buffer, const uint8_t *data, size_t len);

/*
 * function: sgs_buffer_write_to_fd()
 *
 * Copies len bytes of data out of the buffer and writes them to the specified
 * file descriptor.  Writing stops if (a) the buffer runs out of data, or (b) a
 * call to write() on the file descriptor returns any value other than the
 * requested length.  TODO - retuirns?
 */
int sgs_buffer_write_to_fd(sgs_buffer *buffer, int fd);

#endif  /** #ifndef SGS_BUFFER_H */
