package cl.baske.tv.di

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import org.videolan.libvlc.LibVLC

/**
 * Motor de libVLC como ÚNICA instancia para toda la app. Crearlo es caro (carga
 * las librerías nativas y genera el plugin cache de VLC en disco), así que se
 * hace una sola vez y se reusa entre reproducciones. Se pre-calienta en
 * [cl.baske.tv.BaskeApp] en segundo plano para que hasta el primer video abra rápido.
 *
 * `--avcodec-fast`: atajos de decodificación (menos CPU en TV débiles).
 * Buffering: `--network-caching=3000` (3s de prebuffer para aguantar baches de
 * red sin cortarse), `--http-reconnect` (reintenta si la conexión HTTP se cae) y
 * `--clock-jitter=0`/`--clock-synchro=0` (reloj más tolerante → menos micro-cortes).
 * El caché real por reproducción se refina en la Media (ver PlayerScreen).
 */
val playerModule = module {
    single {
        LibVLC(
            androidContext(),
            arrayListOf(
                "--avcodec-fast",
                "--network-caching=3000",
                "--http-reconnect",
                "--clock-jitter=0",
                "--clock-synchro=0",
            ),
        )
    }
}
