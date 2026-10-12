#include <stdint.h>
extern unsigned char *evv_arena_base;
extern void evv_setUpsampleMethod(int method);
__declspec(dllexport) void __stdcall evvSetUpsampleMethod(int method)
{
    if (method == -1 || method == 0 || method == 2 || method == 3 || method == 4)
        evv_setUpsampleMethod(method);
}

/* A private DLL is loaded for each WAV export. eciDelete releases instances,
   but the process-wide arena otherwise survives DLL unloading. The normal
   NVDA engine retains its arena until its own DLL is unloaded. */
__declspec(dllexport) void * __stdcall evvGetExportArenaBase(void)
{
    /* Free externally after FreeLibrary: CRT/TLS destructors still need the
       arena while the DLL is unloading. Never release the live speech DLL. */
    return evv_arena_base;
}
