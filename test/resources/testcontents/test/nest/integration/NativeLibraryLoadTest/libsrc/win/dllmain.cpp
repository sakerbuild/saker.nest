// dllmain.cpp : Defines the entry point for the DLL application.
#include <Windows.h>

#include <jni.h>

#define PROPERTY_UUID "ece381df-4e1c-4175-9ed5-e0fc3ce66adc"
#define PROPERTY_VALUE "lib-loaded-amd64"

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
	JNIEnv* env = NULL;
	if (vm->GetEnv((void**)& env, JNI_VERSION_1_2) == JNI_OK) {
		auto c = env->FindClass("java/lang/System");
		if (c != NULL) {
			auto methodid = env->GetStaticMethodID(c, "setProperty", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;");
			if (methodid != NULL) {
				jstring propname = env->NewStringUTF(PROPERTY_UUID);
				jstring propval = env->NewStringUTF(PROPERTY_VALUE);
				jobject res = env->CallStaticObjectMethod(c, methodid, propname, propval);
				env->DeleteLocalRef(res);
				env->DeleteLocalRef(propval);
				env->DeleteLocalRef(propname);
			}
		}
	}
	return JNI_VERSION_1_2;
}

BOOL APIENTRY DllMain(HMODULE hModule,
	DWORD  ul_reason_for_call,
	LPVOID lpReserved
) {
	switch (ul_reason_for_call) {
		case DLL_PROCESS_ATTACH:
		case DLL_THREAD_ATTACH:
		case DLL_THREAD_DETACH:
		case DLL_PROCESS_DETACH:
			break;
	}
	return TRUE;
}

