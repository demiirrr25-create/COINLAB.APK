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
    val selectedExchange: String = "Tümü",
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
    val exchange: String? = null,
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

    fun selectExchange(exchange: String) {
        _uiState.update { it.copy(selectedExchange = exchange) }
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
            val statusFiltered = when (state.selectedFilter) {
                AirdropFilter.ALL -> state.airdrops
                AirdropFilter.ACTIVE -> state.airdrops.filter { it.status == AirdropStatus.ACTIVE }
                AirdropFilter.UPCOMING -> state.airdrops.filter { it.status == AirdropStatus.UPCOMING }
                AirdropFilter.ENDED -> state.airdrops.filter { it.status == AirdropStatus.ENDED || it.status == AirdropStatus.CONFIRMED }
            }
            return when (state.selectedExchange) {
                "Tümü" -> statusFiltered
                "Topluluk" -> statusFiltered.filter { it.exchange == null }
                else -> statusFiltered.filter { it.exchange == state.selectedExchange }
            }
        }

    private fun loadSampleAirdrops() {
        val day = 86400000L
        val now = System.currentTimeMillis()
        val sampleAirdrops = listOf(
            // === BORSA AİRDROPLARI ===
            Airdrop(
                id = "b1", name = "Binance Launchpool", project = "Binance", exchange = "Binance",
                chain = "BNB Chain", description = "BNB ve FDUSD stake ederek yeni token kazanın. Binance'in en popüler airdrop programı.",
                status = AirdropStatus.ACTIVE, estimatedValue = "\$50-\$500",
                startDate = now - day * 5, endDate = now + day * 25,
                requirements = listOf("Binance hesabı", "BNB veya FDUSD stake et", "Günlük ödül topla"),
                link = "https://launchpad.binance.com", difficulty = AirdropDifficulty.EASY
            ),
            Airdrop(
                id = "b2", name = "Binance Megadrop", project = "Binance", exchange = "Binance",
                chain = "BNB Chain", description = "Web3 görevleri + BNB kilit ile yeni tokenlar kazanın.",
                status = AirdropStatus.ACTIVE, estimatedValue = "\$100-\$1,000",
                startDate = now - day * 10, endDate = now + day * 20,
                requirements = listOf("Binance Web3 cüzdanı", "Görevleri tamamla", "BNB kilitle"),
                link = "https://www.binance.com/en/megadrop", difficulty = AirdropDifficulty.EASY
            ),
            Airdrop(
                id = "b3", name = "Binance HODLer Airdrops", project = "Binance", exchange = "Binance",
                chain = "Multi-chain", description = "Simple Earn'de BNB tutarak otomatik airdrop alın.",
                status = AirdropStatus.ACTIVE, estimatedValue = "\$10-\$200",
                startDate = now - day * 30, endDate = null,
                requirements = listOf("BNB'yi Simple Earn'e yatır", "Snapshot'ta bakiye tut"),
                link = "https://www.binance.com/en/support/announcement", difficulty = AirdropDifficulty.EASY
            ),
            Airdrop(
                id = "b4", name = "Coinbase Learn & Earn", project = "Coinbase", exchange = "Coinbase",
                chain = "Multi-chain", description = "Kısa videolar izle, quiz çöz, kripto kazan. Düzenli yeni kampanyalar.",
                status = AirdropStatus.ACTIVE, estimatedValue = "\$3-\$50",
                startDate = now - day * 60, endDate = null,
                requirements = listOf("Coinbase hesabı (KYC)", "Eğitim videolarını izle", "Quiz sorularını doğru cevapla"),
                link = "https://www.coinbase.com/earn", difficulty = AirdropDifficulty.EASY
            ),
            Airdrop(
                id = "b5", name = "OKX Jumpstart", project = "OKX", exchange = "OKX",
                chain = "Multi-chain", description = "OKB stake ederek yeni proje tokenlarını kazanın.",
                status = AirdropStatus.ACTIVE, estimatedValue = "\$50-\$500",
                startDate = now - day * 7, endDate = now + day * 23,
                requirements = listOf("OKX hesabı", "OKB satın al ve stake et", "Minimum 100 OKB"),
                link = "https://www.okx.com/jumpstart", difficulty = AirdropDifficulty.MEDIUM
            ),
            Airdrop(
                id = "b6", name = "OKX Cryptopedia", project = "OKX", exchange = "OKX",
                chain = "Multi-chain", description = "Web3 görevleri tamamlayarak token ve NFT ödülleri kazanın.",
                status = AirdropStatus.ACTIVE, estimatedValue = "\$10-\$100",
                startDate = now - day * 14, endDate = now + day * 16,
                requirements = listOf("OKX Web3 cüzdanı", "Görevleri tamamla", "DeFi protokolleri kullan"),
                link = "https://www.okx.com/web3/cryptopedia", difficulty = AirdropDifficulty.EASY
            ),
            Airdrop(
                id = "b7", name = "Bybit Launchpad", project = "Bybit", exchange = "Bybit",
                chain = "Multi-chain", description = "MNT veya USDT commit ederek yeni token satışlarına katılın.",
                status = AirdropStatus.ACTIVE, estimatedValue = "\$50-\$800",
                startDate = now - day * 3, endDate = now + day * 12,
                requirements = listOf("Bybit hesabı (KYC)", "MNT veya USDT commit et", "Lottery veya oversubscription"),
                link = "https://www.bybit.com/en/trade/spot/launchpad", difficulty = AirdropDifficulty.MEDIUM
            ),
            Airdrop(
                id = "b8", name = "Bybit Web3 Airdrop Arcade", project = "Bybit", exchange = "Bybit",
                chain = "Multi-chain", description = "Bybit Web3 cüzdanı ile airdrop görevlerini tamamlayın.",
                status = AirdropStatus.ACTIVE, estimatedValue = "\$20-\$300",
                startDate = now - day * 10, endDate = now + day * 20,
                requirements = listOf("Bybit Web3 cüzdanı", "On-chain görevleri tamamla", "Swap, stake, bridge"),
                link = "https://www.bybit.com/en/web3/home", difficulty = AirdropDifficulty.MEDIUM
            ),
            Airdrop(
                id = "b9", name = "KuCoin Spotlight", project = "KuCoin", exchange = "KuCoin",
                chain = "Multi-chain", description = "KCS tutarak yeni token satışlarına erken erişim sağlayın.",
                status = AirdropStatus.UPCOMING, estimatedValue = "\$50-\$400",
                startDate = now + day * 5, endDate = now + day * 35,
                requirements = listOf("KuCoin hesabı", "KCS tut (ortalama bakiye)", "Lottery'ye katıl"),
                link = "https://www.kucoin.com/spotlight-center", difficulty = AirdropDifficulty.EASY
            ),
            Airdrop(
                id = "b10", name = "KuCoin BurningDrop", project = "KuCoin", exchange = "KuCoin",
                chain = "Multi-chain", description = "KCS yak veya stake et, yeni tokenlar kazan.",
                status = AirdropStatus.ACTIVE, estimatedValue = "\$30-\$250",
                startDate = now - day * 8, endDate = now + day * 22,
                requirements = listOf("KCS stake et veya yak", "Farming dönemine katıl", "Ödülleri topla"),
                link = "https://www.kucoin.com/burningdrop", difficulty = AirdropDifficulty.EASY
            ),
            Airdrop(
                id = "b11", name = "Gate.io Startup", project = "Gate.io", exchange = "Gate.io",
                chain = "Multi-chain", description = "Gate.io'nun IEO platformu. GT tutarak yeni projelere erken erişim.",
                status = AirdropStatus.ACTIVE, estimatedValue = "\$20-\$300",
                startDate = now - day * 5, endDate = now + day * 25,
                requirements = listOf("Gate.io hesabı (KYC)", "GT token tut", "Startup'a başvur"),
                link = "https://www.gate.io/startup", difficulty = AirdropDifficulty.EASY
            ),
            Airdrop(
                id = "b12", name = "Bitget Launchpad", project = "Bitget", exchange = "Bitget",
                chain = "Multi-chain", description = "BGB stake ederek yeni token airdropları ve IDO'lara katılın.",
                status = AirdropStatus.ACTIVE, estimatedValue = "\$30-\$500",
                startDate = now - day * 6, endDate = now + day * 24,
                requirements = listOf("Bitget hesabı", "BGB stake et", "Launchpad etkinliğine katıl"),
                link = "https://www.bitget.com/launchpad", difficulty = AirdropDifficulty.EASY
            ),
            Airdrop(
                id = "b13", name = "Bitget PoolX", project = "Bitget", exchange = "Bitget",
                chain = "Multi-chain", description = "Token stake ederek yeni proje tokenları mine edin.",
                status = AirdropStatus.ACTIVE, estimatedValue = "\$10-\$200",
                startDate = now - day * 12, endDate = now + day * 18,
                requirements = listOf("Desteklenen token stake et", "Pool'a katıl", "Günlük ödül topla"),
                link = "https://www.bitget.com/poolx", difficulty = AirdropDifficulty.EASY
            ),
            Airdrop(
                id = "b14", name = "MEXC Kickstarter", project = "MEXC", exchange = "MEXC",
                chain = "Multi-chain", description = "MX token ile oy kullanarak yeni tokenlara ücretsiz erişim sağlayın.",
                status = AirdropStatus.ACTIVE, estimatedValue = "\$10-\$150",
                startDate = now - day * 4, endDate = now + day * 26,
                requirements = listOf("MEXC hesabı", "MX token tut", "Kickstarter'a oy ver"),
                link = "https://www.mexc.com/kickstarter", difficulty = AirdropDifficulty.EASY
            ),
            // === TOPLULUK / ZİNCİR AİRDROPLARI ===
            Airdrop(
                id = "c1", name = "LayerZero Season 2", project = "LayerZero",
                chain = "Multi-chain", description = "LayerZero omnichain mesajlaşma protokolü ikinci sezon airdrop'u.",
                status = AirdropStatus.ACTIVE, estimatedValue = "\$500-\$5,000",
                startDate = now - day * 30, endDate = now + day * 60,
                requirements = listOf("Stargate köprüsü kullan", "5+ farklı zincirde işlem", "OFT/ONFT mesaj gönder", "3+ ay aktif ol"),
                link = "https://layerzero.network", difficulty = AirdropDifficulty.MEDIUM
            ),
            Airdrop(
                id = "c2", name = "Berachain Artio", project = "Berachain",
                chain = "Berachain", description = "Berachain bArtio testnet ve mainnet airdrop'u. Proof of Liquidity konsensüs.",
                status = AirdropStatus.ACTIVE, estimatedValue = "\$500-\$8,000",
                startDate = now - day * 45, endDate = null,
                requirements = listOf("Berachain testnet kullan", "BEX'te swap yap", "Honey mint et", "Validator delegate et"),
                link = "https://berachain.com", difficulty = AirdropDifficulty.MEDIUM
            ),
            Airdrop(
                id = "c3", name = "EigenLayer Season 2", project = "EigenLayer",
                chain = "Ethereum", description = "EigenLayer restaking protokolü ikinci sezon EIGEN token dağıtımı.",
                status = AirdropStatus.ACTIVE, estimatedValue = "\$300-\$5,000",
                startDate = now - day * 20, endDate = now + day * 70,
                requirements = listOf("ETH veya LST restake et", "EigenLayer operatörüne delegate et", "Min 0.1 ETH"),
                link = "https://eigenlayer.xyz", difficulty = AirdropDifficulty.MEDIUM
            ),
            Airdrop(
                id = "c4", name = "Hyperliquid Points", project = "Hyperliquid",
                chain = "Hyperliquid L1", description = "Hyperliquid perp DEX'te işlem yaparak puan kazanın. L1 token dağıtımı yakın.",
                status = AirdropStatus.ACTIVE, estimatedValue = "\$1,000-\$20,000",
                startDate = now - day * 60, endDate = null,
                requirements = listOf("Hyperliquid'de trade yap", "HLP vault'a likidite sağla", "Referral programına katıl"),
                link = "https://hyperliquid.xyz", difficulty = AirdropDifficulty.HARD
            ),
            Airdrop(
                id = "c5", name = "Monad Testnet", project = "Monad",
                chain = "Monad", description = "Yeni nesil EVM L1 blockchain. Erken kullanıcılara büyük airdrop bekleniyor.",
                status = AirdropStatus.ACTIVE, estimatedValue = "\$500-\$10,000",
                startDate = now - day * 20, endDate = null,
                requirements = listOf("Monad testnet'e katıl", "Discord'a gir", "Testnet faucet al", "Swap & stake yap"),
                link = "https://monad.xyz", difficulty = AirdropDifficulty.EASY
            ),
            Airdrop(
                id = "c6", name = "Scroll Sessions", project = "Scroll",
                chain = "Scroll", description = "Scroll zkEVM L2 ağı Marks programı. DeFi kullanımıyla puan kazan.",
                status = AirdropStatus.ACTIVE, estimatedValue = "\$200-\$3,000",
                startDate = now - day * 40, endDate = null,
                requirements = listOf("Scroll'a köprüle", "DeFi protokollerinde likidite sağla", "Scroll Canvas badge topla"),
                link = "https://scroll.io", difficulty = AirdropDifficulty.MEDIUM
            ),
            Airdrop(
                id = "c7", name = "Blast Season 2", project = "Blast",
                chain = "Blast L2", description = "Blast L2 ikinci sezon Gold ve Points. ETH/USDB yield + DApp kullanımı.",
                status = AirdropStatus.ACTIVE, estimatedValue = "\$200-\$4,000",
                startDate = now - day * 25, endDate = now + day * 45,
                requirements = listOf("Blast'a köprüle", "DApp'lerde Gold kazan", "Thruster/Juice swap kullan"),
                link = "https://blast.io", difficulty = AirdropDifficulty.MEDIUM
            ),
            Airdrop(
                id = "c8", name = "Starknet DeFi Spring", project = "Starknet",
                chain = "Starknet", description = "Starknet ekosisteminde DeFi kullanarak STRK ödülleri kazanın.",
                status = AirdropStatus.ACTIVE, estimatedValue = "\$100-\$2,000",
                startDate = now - day * 35, endDate = now + day * 55,
                requirements = listOf("Starknet'te aktif ol", "Ekosphere DeFi kullan", "Likidite sağla"),
                link = "https://starknet.io", difficulty = AirdropDifficulty.MEDIUM
            )
        )

        _uiState.update { it.copy(airdrops = sampleAirdrops) }
    }
}
