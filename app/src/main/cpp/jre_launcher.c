#include <dlfcn.h>
#include <errno.h>
#include <jni.h>
#include <pthread.h>
#include <stdio.h>
#include <fcntl.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include "log.h"
#include "utils.h"

/* Must match QuestCraft JRE libjvm.so build string */
#define FULL_VERSION "21.0.3-internal-adhoc.runner.openjdk"
#define DOT_VERSION "21"

typedef jint JLI_Launch_func(
        int argc, char **argv,
        int jargc, const char **jargv,
        int appclassc, const char **appclassv,
        const char *fullversion,
        const char *dotversion,
        const char *pname,
        const char *lname,
        jboolean javaargs,
        jboolean cpwildcard,
        jboolean javaw,
        jint ergo
);

static int stdin_pipe[2] = {-1, -1};
static pthread_mutex_t stdin_mutex = PTHREAD_MUTEX_INITIALIZER;

static void ensure_stdin_pipe(void) {
    if (stdin_pipe[0] >= 0) return;
    if (pipe(stdin_pipe) != 0) {
        LOGE("Failed to create stdin pipe: %s", strerror(errno));
        return;
    }
    dup2(stdin_pipe[0], STDIN_FILENO);
}

static void redirect_output_to_log(const char *log_dir) {
    if (log_dir == NULL || log_dir[0] == '\0') return;
    char path[512];
    snprintf(path, sizeof(path), "%s/jvm.log", log_dir);
    int fd = open(path, O_WRONLY | O_CREAT | O_APPEND, 0644);
    if (fd < 0) {
        LOGE("Failed to open log %s: %s", path, strerror(errno));
        return;
    }
    dup2(fd, STDOUT_FILENO);
    dup2(fd, STDERR_FILENO);
    setvbuf(stdout, NULL, _IONBF, 0);
    setvbuf(stderr, NULL, _IONBF, 0);
    close(fd);
    LOGI("Redirected stdout/stderr to %s", path);
}

static jint launch_jvm_internal(int argc, char **argv, const char *log_dir) {
    const char *java_home = getenv("JAVA_HOME");
    char jli_path[512];
    void *libjli = NULL;

    if (java_home != NULL) {
        snprintf(jli_path, sizeof(jli_path), "%s/lib/libjli.so", java_home);
        libjli = dlopen(jli_path, RTLD_LAZY | RTLD_GLOBAL);
        if (libjli == NULL) {
            LOGW("dlopen %s failed: %s", jli_path, dlerror());
        }
    }
    if (libjli == NULL) {
        libjli = dlopen("libjli.so", RTLD_LAZY | RTLD_GLOBAL);
    }
    if (libjli == NULL) {
        LOGE("dlopen libjli.so failed: %s", dlerror());
        return -1;
    }

    JLI_Launch_func *pJLI_Launch = (JLI_Launch_func *) dlsym(libjli, "JLI_Launch");
    if (pJLI_Launch == NULL) {
        LOGE("JLI_Launch symbol not found");
        return -1;
    }

    LOGI("Calling JLI_Launch with %d args (JAVA_HOME=%s)", argc, java_home ? java_home : "(unset)");
    redirect_output_to_log(log_dir);
    jint result = pJLI_Launch(
            argc, argv,
            0, NULL,
            0, NULL,
            FULL_VERSION,
            DOT_VERSION,
            argc > 0 ? argv[0] : "java",
            argc > 0 ? argv[0] : "java",
            JNI_FALSE,
            JNI_TRUE,
            JNI_FALSE,
            0
    );
    LOGI("JLI_Launch returned %d", result);
    return result;
}

JNIEXPORT jint JNICALL
Java_com_mchost_runtime_JvmNative_launchJVM(
        JNIEnv *env,
        jclass clazz,
        jobjectArray argsArray,
        jstring javaHome,
        jstring ldLibraryPath,
        jstring tmpDir,
        jstring workDir,
        jstring logDir
) {
    (void) clazz;
    (void) javaHome;
    (void) ldLibraryPath;
    (void) tmpDir;

    if (argsArray == NULL) {
        LOGE("argsArray is null");
        return -1;
    }

    const char *log_dir_c = NULL;
    if (logDir != NULL) {
        log_dir_c = (*env)->GetStringUTFChars(env, logDir, NULL);
    }

    if (workDir != NULL) {
        const char *wd = (*env)->GetStringUTFChars(env, workDir, NULL);
        if (wd != NULL) {
            if (chdir(wd) != 0) {
                LOGE("chdir(%s) failed: %s", wd, strerror(errno));
            }
            (*env)->ReleaseStringUTFChars(env, workDir, wd);
        }
    }

    ensure_stdin_pipe();

    int argc = (*env)->GetArrayLength(env, argsArray);
    char **argv = convert_to_char_array(env, argsArray);
    if (argv == NULL) return -1;

    jint result = launch_jvm_internal(argc, argv, log_dir_c);
    free_char_array(env, argsArray, argv);
    if (logDir != NULL && log_dir_c != NULL) {
        (*env)->ReleaseStringUTFChars(env, logDir, log_dir_c);
    }
    return result;
}

JNIEXPORT void JNICALL
Java_com_mchost_runtime_JvmNative_writeStdin(JNIEnv *env, jclass clazz, jbyteArray data) {
    (void) clazz;
    if (data == NULL || stdin_pipe[1] < 0) return;

    jsize len = (*env)->GetArrayLength(env, data);
    if (len <= 0) return;

    jbyte *bytes = (*env)->GetByteArrayElements(env, data, NULL);
    if (bytes == NULL) return;

    pthread_mutex_lock(&stdin_mutex);
    write(stdin_pipe[1], bytes, (size_t) len);
    pthread_mutex_unlock(&stdin_mutex);

    (*env)->ReleaseByteArrayElements(env, data, bytes, JNI_ABORT);
}

JNIEXPORT jboolean JNICALL
Java_com_mchost_runtime_JvmNative_dlopen(JNIEnv *env, jclass clazz, jstring path) {
    (void) clazz;
    const char *pathUtf = (*env)->GetStringUTFChars(env, path, NULL);
    void *handle = dlopen(pathUtf, RTLD_GLOBAL | RTLD_LAZY);
    if (handle == NULL) {
        LOGE("dlopen %s failed: %s", pathUtf, dlerror());
    } else {
        LOGD("dlopen %s ok", pathUtf);
    }
    (*env)->ReleaseStringUTFChars(env, path, pathUtf);
    return handle != NULL ? JNI_TRUE : JNI_FALSE;
}

typedef void (*android_update_LD_LIBRARY_PATH_t)(const char *);

JNIEXPORT void JNICALL
Java_com_mchost_runtime_JvmNative_setLdLibraryPath(JNIEnv *env, jclass clazz, jstring ldPath) {
    (void) clazz;
    void *libdl = dlopen("libdl.so", RTLD_LAZY);
    if (libdl == NULL) {
        LOGE("dlopen libdl.so failed");
        return;
    }

    android_update_LD_LIBRARY_PATH_t updateFn =
            (android_update_LD_LIBRARY_PATH_t) dlsym(libdl, "android_update_LD_LIBRARY_PATH");
    if (updateFn == NULL) {
        updateFn = (android_update_LD_LIBRARY_PATH_t) dlsym(
                libdl, "__loader_android_update_LD_LIBRARY_PATH");
    }
    if (updateFn == NULL) {
        LOGE("android_update_LD_LIBRARY_PATH not found");
        return;
    }

    const char *pathUtf = (*env)->GetStringUTFChars(env, ldPath, NULL);
    updateFn(pathUtf);
    (*env)->ReleaseStringUTFChars(env, ldPath, pathUtf);
}

JNIEXPORT jint JNICALL
Java_com_mchost_runtime_JvmNative_chdir(JNIEnv *env, jclass clazz, jstring path) {
    (void) clazz;
    const char *pathUtf = (*env)->GetStringUTFChars(env, path, NULL);
    int result = chdir(pathUtf);
    if (result != 0) {
        LOGE("chdir(%s) failed: %s", pathUtf, strerror(errno));
    }
    (*env)->ReleaseStringUTFChars(env, path, pathUtf);
    return result;
}
