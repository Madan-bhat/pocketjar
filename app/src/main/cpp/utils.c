#include <jni.h>
#include <stdlib.h>
#include <string.h>

#include "utils.h"

char **convert_to_char_array(JNIEnv *env, jobjectArray jstringArray) {
    int num_rows = (*env)->GetArrayLength(env, jstringArray);
    char **cArray = (char **) malloc(num_rows * sizeof(char *));
    if (cArray == NULL) return NULL;

    for (int i = 0; i < num_rows; i++) {
        jstring row = (jstring) (*env)->GetObjectArrayElement(env, jstringArray, i);
        cArray[i] = (char *) (*env)->GetStringUTFChars(env, row, 0);
    }
    return cArray;
}

void free_char_array(JNIEnv *env, jobjectArray jstringArray, char **charArray) {
    int num_rows = (*env)->GetArrayLength(env, jstringArray);
    for (int i = 0; i < num_rows; i++) {
        jstring row = (jstring) (*env)->GetObjectArrayElement(env, jstringArray, i);
        (*env)->ReleaseStringUTFChars(env, row, charArray[i]);
    }
    free(charArray);
}
