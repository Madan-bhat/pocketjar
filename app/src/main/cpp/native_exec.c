#include <jni.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/wait.h>
#include <signal.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>

#include "utils.h"

static char **build_envp(JNIEnv *env, jobjectArray jEnv) {
    if (jEnv == NULL) return NULL;
    int count = (*env)->GetArrayLength(env, jEnv);
    char **envp = (char **) malloc((count + 1) * sizeof(char *));
    if (envp == NULL) return NULL;
    for (int i = 0; i < count; i++) {
        jstring row = (jstring) (*env)->GetObjectArrayElement(env, jEnv, i);
        envp[i] = (char *) (*env)->GetStringUTFChars(env, row, 0);
    }
    envp[count] = NULL;
    return envp;
}

static void free_envp(JNIEnv *env, jobjectArray jEnv, char **envp) {
    if (envp == NULL || jEnv == NULL) return;
    int count = (*env)->GetArrayLength(env, jEnv);
    for (int i = 0; i < count; i++) {
        jstring row = (jstring) (*env)->GetObjectArrayElement(env, jEnv, i);
        (*env)->ReleaseStringUTFChars(env, row, envp[i]);
    }
    free(envp);
}

JNIEXPORT jint JNICALL
Java_com_mchost_network_NativeExec_nativeSpawn(
        JNIEnv *env,
        jclass clazz,
        jstring jBinary,
        jobjectArray jArgs,
        jobjectArray jEnv,
        jstring jLogPath) {
    (void) clazz;
    const char *binary = (*env)->GetStringUTFChars(env, jBinary, NULL);
    const char *log_path = (*env)->GetStringUTFChars(env, jLogPath, NULL);
    if (binary == NULL || log_path == NULL) return -1;

    char **argv = convert_to_char_array(env, jArgs);
    char **envp = build_envp(env, jEnv);
    if (argv == NULL) {
        (*env)->ReleaseStringUTFChars(env, jBinary, binary);
        (*env)->ReleaseStringUTFChars(env, jLogPath, log_path);
        return -1;
    }

    int log_fd = open(log_path, O_WRONLY | O_CREAT | O_TRUNC, 0644);
    pid_t pid = fork();
    if (pid == 0) {
        if (log_fd >= 0) {
            dup2(log_fd, STDOUT_FILENO);
            dup2(log_fd, STDERR_FILENO);
            close(log_fd);
        }
        execve(binary, argv, envp);
        _exit(127);
    }

    if (log_fd >= 0) close(log_fd);
    free_char_array(env, jArgs, argv);
    free_envp(env, jEnv, envp);
    (*env)->ReleaseStringUTFChars(env, jBinary, binary);
    (*env)->ReleaseStringUTFChars(env, jLogPath, log_path);
    return pid < 0 ? -1 : (jint) pid;
}

JNIEXPORT jboolean JNICALL
Java_com_mchost_network_NativeExec_nativeIsAlive(JNIEnv *env, jclass clazz, jint pid) {
    (void) env;
    (void) clazz;
    if (pid <= 0) return JNI_FALSE;
    int status = 0;
    pid_t result = waitpid((pid_t) pid, &status, WNOHANG);
    if (result == 0) return JNI_TRUE;
    return JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_mchost_network_NativeExec_nativeKill(JNIEnv *env, jclass clazz, jint pid) {
    (void) env;
    (void) clazz;
    if (pid <= 0) return;
    kill((pid_t) pid, SIGTERM);
}
