package com.JavHey

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class JavHeyPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(JavHey())

        registerExtractorAPI(Hglink())
        registerExtractorAPI(Haxloppd())
        registerExtractorAPI(Minochinos()) 
        registerExtractorAPI(GoTv())
        
        // registerExtractorAPI(Bysebuho())  <-- HAPUS INI
    }
}
