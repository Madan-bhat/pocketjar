#ifndef MCHOST_UTILS_H
#define MCHOST_UTILS_H

#include <jni.h>

char **convert_to_char_array(JNIEnv *env, jobjectArray jstringArray);
void free_char_array(JNIEnv *env, jobjectArray jstringArray, char **charArray);

#endif
