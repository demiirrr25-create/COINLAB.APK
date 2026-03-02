package com.coinlab.app.ui.airdrop

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class AirdropUiState(
    val airdrops: List<Airdrop> = emptyList(),
    val selectedFilter: AirdropFilter = AirdropFilter.ALL,
    val isLoading: Boolean = false
)

enum class AirdropFilter(val title: String) {
    ALL("Tümü"),
    ACTIVE("Aktif"),
    UPCOMING("Yaklaşan"),
    ENDED("Biten")
}

data class Airdrop(
    val id: String,
    val name: String,
    val project: String,
    val projectLogo: String? = null,
    val chain: String,
    val description: String,
    val status: AirdropStatus,
    val estimatedValue: String?,
    val startDate: Long,
    val endDate: Long?,
    val requirements: List<String>,
    val link: String?,
    val difficulty: AirdropDifficulty,
    val isParticipating: Boolean = false
)

enum class AirdropStatus(val label: String, val emoji: String) {
    ACTIVE("Aktif", "🟢"),
    UPCOMING("Yaklaşan", "🟡"),
    ENDED("Biten", "🔴"),
    CONFIRMED("Onaylandı", "✅")
}

enum class AirdropDifficulty(val label: String, val emoji: String) {
    EASY("Kolay", "🟢"),
    MEDIUM("Orta", "🟡"),
    HARD("Zor", "🔴")
}

@HiltViewModel
class AirdropViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(AirdropUiState())
    val uiState: StateFlow<AirdropUiState> = _uiState.asStateFlow()

    init {
        loadSampleAirdrops()
    }

    fun selectFilter(filter: AirdropFilter) {
        _uiState.update { it.copy(selectedFilter = filter) }
    }

    fun toggleParticipation(airdropId: String) {
        _uiState.update { state ->
            state.copy(
                airdrops = state.airdrops.map { airdrop ->
                    if (airdrop.id == airdropId) {
                        airdrop.copy(isParticipating = !airdrop.isParticipating)
                    } else airdrop
                }
            )
        }
    }

    val filteredAirdrops: List<Airdrop>
        get() {
            val state = _uiState.value
            return when (state.selectedFilter) {
                AirdropFilter.ALL -> state.airdrops
                AirdropFilter.ACTIVE -> state.airdrops.filter { it.status == AirdropStatus.ACTIVE }
                AirdropFilter.UPCOMING -> state.airdrops.filter { it.status == AirdropStatus.UPCOMING }
                AirdropFilter.ENDED -> state.airdrops.filter { it.status == AirdropStatus.ENDED || it.status == AirdropStatus.CONFIRMED }
            }
        }

    private fun loadSampleAirdrops() {
        val sampleAirdrops = listOf(
            Airdrop(
                id = "1",
                name = "LayerZero Season 2",
                project = "LayerZero",
                chain = "Multi-chain",
                description = "LayerZero omnichain mesajlaşma protokolü ikinci sezon airdrop'u. Köprü işlemleri ve mesajlaşma kullanımı gerekli.",
                status = AirdropStatus.ACTIVE,
                estimatedValue = "\$500-\$5,000",
                startDate = System.currentTimeMillis() - 86400000L * 30,
                endDate = System.currentTimeMillis() + 86400000L * 60,
                requirements = listOf(
                    "Stargate köprüsü kullan",
                    "En az 5 farklı zincirde işlem yap",
                    "Mesaj gönder (OFT/ONFT)",
                    "3+ ay aktif ol"
                ),
                link = "https://layerzero.network",
                difficulty = AirdropDifficulty.MEDIUM
            ),
            Airdrop(
                id = "2",
                name = "zkSync Era Rewards",
                project = "zkSync",
                chain = "zkSync Era",
                description = "zkSync Era L2 ağında aktif kullanıcılara token dağıtımı. Çeşitli DeFi protokollerinde işlem gerekli.",
                status = AirdropStatus.ACTIVE,
                estimatedValue = "\$200-\$3,000",
                startDate = System.currentTimeMillis() - 86400000L * 60,
                endDate = null,
                requirements = listOf(
                    "zkSync Era'da köprüle",
                    "SyncSwap'te swap yap",
                    "Liquidity sağla",
                    "NFT mint et",
                    "6+ ay aktif ol"
                ),
                link = "https://zksync.io",
                difficulty = AirdropDifficulty.HARD
            ),
            Airdrop(
                id = "3",
                name = "Scroll Airdrop",
                project = "Scroll",
                chain = "Scroll",
                description = "Scroll zkEVM L2 ağı token dağıtımı. Scroll mainnet'te aktif kullanım ve DeFi etkileşimi.",
                status = AirdropStatus.UPCOMING,
                estimatedValue = "\$300-\$2,000",
                startDate = System.currentTimeMillis() + 86400000L * 15,
                endDate = null,
                requirements = listOf(
                    "Scroll'a köprüle",
                    "Ambient Finance kullan",
                    "Scroll Canvas NFT mint",
                    "Farklı dApps etkileşimi"
                ),
                link = "https://scroll.io",
                difficulty = AirdropDifficulty.MEDIUM
            ),
            Airdrop(
                id = "4",
                name = "Starknet STRK Round 2",
                project = "Starknet",
                chain = "Starknet",
                description = "Starknet ikinci airdrop turu. İlk turda almayanlar veya yeni kullanıcılar için ek dağıtım.",
                status = AirdropStatus.UPCOMING,
                estimatedValue = "\$100-\$1,500",
                startDate = System.currentTimeMillis() + 86400000L * 30,
                endDate = null,
                requirements = listOf(
                    "Starknet'te aktif ol",
                    "0.005+ ETH köprüle",
                    "3+ farklı dApp kullan",
                    "Hacim >= \$1000"
                ),
                link = "https://starknet.io",
                difficulty = AirdropDifficulty.EASY
            ),
            Airdrop(
                id = "5",
                name = "Arbitrum ARB Season",
                project = "Arbitrum",
                chain = "Arbitrum",
                description = "Arbitrum DAO yönetişim ve ekosistem kullanıcılarına ödül programı.",
                status = AirdropStatus.CONFIRMED,
                estimatedValue = "\$150-\$800",
                startDate = System.currentTimeMillis() - 86400000L * 90,
                endDate = System.currentTimeMillis() - 86400000L * 10,
                requirements = listOf(
                    "Arbitrum'da işlem yap",
                    "GMX/Camelot kullan",
                    "DAO oylamaya katıl"
                ),
                link = "https://arbitrum.io",
                difficulty = AirdropDifficulty.EASY
            ),
            Airdrop(
                id = "6",
                name = "Monad Testnet",
                project = "Monad",
                chain = "Monad",
                description = "Monad yeni nesil EVM L1 blockchain testnet katılımı. Erken kullanıcılara token dağıtımı bekleniyor.",
                status = AirdropStatus.ACTIVE,
                estimatedValue = "\$500-\$10,000",
                startDate = System.currentTimeMillis() - 86400000L * 20,
                endDate = null,
                requirements = listOf(
                    "Monad testnet'e katıl",
                    "Discord'a gir",
                    "Testnet faucet al",
                    "Swap & stake işlemleri yap"
                ),
                link = "https://monad.xyz",
                difficulty = AirdropDifficulty.EASY
            )
        )

        _uiState.update { it.copy(airdrops = sampleAirdrops) }
    }
}
