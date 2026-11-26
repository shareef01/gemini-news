package com.aus.gemini01.ui

import com.aus.gemini01.data.Article
import com.aus.gemini01.data.ai.AiCacheKeys
import com.aus.gemini01.data.ai.AiError
import com.aus.gemini01.data.ai.AiFeature
import com.aus.gemini01.data.ai.AiRepository
import com.aus.gemini01.data.ai.AiRequestException
import com.aus.gemini01.data.ai.AiResult
import com.google.firebase.ai.GenerativeModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class InsightsManager(
    private val aiRepository: AiRepository,
    private val generativeModel: GenerativeModel,
    private val scope: CoroutineScope,
    private val errorEvents: MutableSharedFlow<String>
) {
    private val _summaryState = MutableStateFlow<AiResult?>(null)
    val summaryState: StateFlow<AiResult?> = _summaryState.asStateFlow()

    private val _isSummarizing = MutableStateFlow(false)
    val isSummarizing: StateFlow<Boolean> = _isSummarizing.asStateFlow()

    private val _readingStats = MutableStateFlow<AiResult?>(null)
    val readingStats: StateFlow<AiResult?> = _readingStats.asStateFlow()

    private val _isAnalysingStats = MutableStateFlow(false)
    val isAnalysingStats: StateFlow<Boolean> = _isAnalysingStats.asStateFlow()

    private val _trendingTopics = MutableStateFlow<AiResult?>(null)
    val trendingTopics: StateFlow<AiResult?> = _trendingTopics.asStateFlow()

    private val _isAnalysingTrends = MutableStateFlow(false)
    val isAnalysingTrends: StateFlow<Boolean> = _isAnalysingTrends.asStateFlow()

    private val _isAnalysingSmartThemes = MutableStateFlow(false)
    val isAnalysingSmartThemes: StateFlow<Boolean> = _isAnalysingSmartThemes.asStateFlow()

    private val _smartThemes = MutableStateFlow<Map<String, List<Article>>>(emptyMap())
    val smartThemes: StateFlow<Map<String, List<Article>>> = _smartThemes.asStateFlow()

    private val _selectedSmartTheme = MutableStateFlow<String?>(null)
    val selectedSmartTheme: StateFlow<String?> = _selectedSmartTheme.asStateFlow()

    private val _isAnalysingLocations = MutableStateFlow(false)
    val isAnalysingLocations: StateFlow<Boolean> = _isAnalysingLocations.asStateFlow()

    private val _newsLocations = MutableStateFlow<List<NewsLocation>>(emptyList())
    val newsLocations: StateFlow<List<NewsLocation>> = _newsLocations.asStateFlow()

    private val _isAnalysingInterests = MutableStateFlow(false)
    val isAnalysingInterests: StateFlow<Boolean> = _isAnalysingInterests.asStateFlow()

    private var summaryJob: Job? = null
    private var trendsJob: Job? = null
    private var statsJob: Job? = null
    private var themesJob: Job? = null
    private var locationsJob: Job? = null

    private var summaryGeneration = 0L
    private var trendsGeneration = 0L
    private var statsGeneration = 0L
    private var themesGeneration = 0L
    private var locationsGeneration = 0L
    private var lastSummarizedArticle: Article? = null

    fun summarizeArticle(article: Article, preferredLanguage: String) {
        val generation = ++summaryGeneration
        lastSummarizedArticle = article
        _isSummarizing.value = true
        _summaryState.value = null

        summaryJob?.cancel()
        summaryJob = scope.launch(Dispatchers.IO) {
            try {
                val hasBody = !article.description.isNullOrBlank() || !article.content.isNullOrBlank()
                val prompt = if (hasBody) {
                    """
                        $PROMPT_INJECTION_GUARD

                        Analyze this news article and provide a clear, beautifully structured executive summary with these exact sections:

                        ## 📌 Key Takeaways
                        - [Key point 1]
                        - [Key point 2]
                        - [Key point 3]

                        ## 💡 Sentiment & Tone
                        - Sentiment: [Positive | Negative | Neutral] — [One brief sentence explaining the tone and impact]

                        ## 🏢 Key Entities & Context
                        - [Notable people, companies, or organizations involved]

                        IMPORTANT: Provide the entire response in $preferredLanguage. Format with clean markdown headers and bullet points. Do not include conversational preambles.

                        [[DATA]]
                        Title: ${sanitizeForPrompt(article.title)}
                        Description: ${sanitizeForPrompt(article.description)}
                        Content: ${sanitizeForPrompt(article.content)}
                        [[/DATA]]
                    """.trimIndent()
                } else {
                    """
                        $PROMPT_INJECTION_GUARD

                        Only the headline and publisher domain are available for this news item (no article body was supplied by the feed). Provide an honest, concise headline brief with these exact sections, without fabricating unreported article details:

                        ## 📌 Key Takeaways
                        - [What the headline directly reports]
                        - [Note clearly that full article details require opening the publisher link]

                        ## 💡 Sentiment & Tone
                        - Sentiment: [Positive | Negative | Neutral] — [One brief sentence describing the headline's framing]

                        ## 🏢 Key Entities & Context
                        - [People, places, or organizations named in the headline, plus brief general background if well-known]

                        IMPORTANT: Provide the entire response in $preferredLanguage. Format with clean markdown headers and bullet points. Do not include conversational preambles.

                        [[DATA]]
                        Title: ${sanitizeForPrompt(article.title)}
                        Source: ${sanitizeForPrompt(article.source.name)}
                        [[/DATA]]
                    """.trimIndent()
                }

                val text = aiRepository.cachedOrFetch(
                    cacheKey = AiCacheKeys.summary(
                        article.url,
                        preferredLanguage,
                        AiCacheKeys.articleEvidenceFingerprint(
                            article.title,
                            article.description,
                            article.content,
                            article.source.name
                        )
                    ),
                    feature = AiFeature.SUMMARY,
                    articleUrl = article.url
                ) {
                    withTimeout(90_000) { generativeModel.generateContent(prompt) }.text
                        ?: throw IllegalStateException("Empty summary response")
                }
                if (generation == summaryGeneration) {
                    _summaryState.value = AiResult.Success(text)
                }
            } catch (e: TimeoutCancellationException) {
                if (generation == summaryGeneration) {
                    _summaryState.value = AiResult.Failure(AiError.Timeout)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiRequestException) {
                if (generation == summaryGeneration) {
                    _summaryState.value = AiResult.Failure(e.error)
                }
            } catch (e: Exception) {
                if (generation == summaryGeneration) {
                    _summaryState.value = AiResult.Failure(AiError.Unknown(e.message))
                }
            } finally {
                if (generation == summaryGeneration) {
                    _isSummarizing.value = false
                }
            }
        }
    }

    fun retrySummarize(preferredLanguage: String) {
        lastSummarizedArticle?.let { summarizeArticle(it, preferredLanguage) }
    }

    fun clearSummary() {
        _summaryState.value = null
    }

    fun analyzeTrendingTopics(articles: List<Article>, preferredLanguage: String) {
        if (articles.isEmpty()) return

        val generation = ++trendsGeneration
        _isAnalysingTrends.value = true
        _trendingTopics.value = null

        trendsJob?.cancel()
        trendsJob = scope.launch(Dispatchers.IO) {
            try {
                val topArticles = articles.take(15)
                val titles = topArticles.joinToString("\n") { "- ${sanitizeForPrompt(it.title)}" }
                val prompt = """
                    $PROMPT_INJECTION_GUARD

                    Analyze only these available headlines and identify up to 5 narratives
                    represented in this feed. Do not describe them as objectively the world's
                    most important stories, and do not add facts not present in the headlines.
                    
                    Format each narrative cleanly using markdown:
                    ## 1. [Catchy Trend Title]
                    - [One to two concise sentences summarizing the trend and why it matters]
                    
                    Output the entire response in $preferredLanguage.

                    Headlines:

                    [[DATA]]
                    $titles
                    [[/DATA]]
                """.trimIndent()

                val text = aiRepository.cachedOrFetch(
                    cacheKey = AiCacheKeys.trends(
                        topArticles.map { it.url },
                        preferredLanguage,
                        topArticles.map {
                            AiCacheKeys.articleEvidenceFingerprint(it.title, it.description, it.content)
                        }
                    ),
                    feature = AiFeature.TRENDS
                ) {
                    withTimeout(90_000) { generativeModel.generateContent(prompt) }.text
                        ?: throw IllegalStateException("Empty trends response")
                }
                if (generation == trendsGeneration) {
                    _trendingTopics.value = AiResult.Success(text)
                }
            } catch (e: TimeoutCancellationException) {
                if (generation == trendsGeneration) {
                    _trendingTopics.value = AiResult.Failure(AiError.Timeout)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiRequestException) {
                if (generation == trendsGeneration) {
                    _trendingTopics.value = AiResult.Failure(e.error)
                }
            } catch (e: Exception) {
                if (generation == trendsGeneration) {
                    _trendingTopics.value = AiResult.Failure(AiError.Unknown(e.message))
                }
            } finally {
                if (generation == trendsGeneration) {
                    _isAnalysingTrends.value = false
                }
            }
        }
    }

    fun clearTrendingTopics() {
        _trendingTopics.value = null
    }

    fun fetchReadingStats(recentArticles: List<Article>, preferredLanguage: String) {
        if (recentArticles.isEmpty()) {
            _readingStats.value = AiResult.Success(
                "You haven't read enough articles yet to generate insights! Start browsing the feed to build your reading profile."
            )
            return
        }

        val generation = ++statsGeneration
        _isAnalysingStats.value = true
        _readingStats.value = null

        statsJob?.cancel()
        statsJob = scope.launch(Dispatchers.IO) {
            try {
                val sampledArticles = recentArticles.take(20)
                val historyContext = sampledArticles.joinToString("\n") {
                    "- ${sanitizeForPrompt(it.title)} (${sanitizeForPrompt(it.source.name)})"
                }

                val prompt = """
                    $PROMPT_INJECTION_GUARD

                    Based on these news articles the user read recently, generate a personalized weekly news intelligence digest:

                    ## 📊 Your Reading Profile
                    - [Top themes or topics that dominate their reading]
                    - [Diversity of sources/perspectives they consume]

                    ## 🧠 Key Takeaways & Knowledge Gained
                    - [2-3 bullet points synthesizing what they learned this week]

                    ## 🔮 Suggested Horizons
                    - [1-2 topics or blindspots they might want to explore next]

                    Keep it engaging, analytical, and concise.

                    Articles Read:
                    [[DATA]]
                    $historyContext
                    [[/DATA]]

                    Output the entire response in $preferredLanguage.
                """.trimIndent()

                val text = aiRepository.cachedOrFetch(
                    cacheKey = AiCacheKeys.stats(
                        sampledArticles.map { it.url },
                        preferredLanguage,
                        sampledArticles.map {
                            AiCacheKeys.articleEvidenceFingerprint(
                                title = it.title,
                                description = null,
                                content = null,
                                sourceName = it.source.name
                            )
                        }
                    ),
                    feature = AiFeature.STATS
                ) {
                    withTimeout(90_000) { generativeModel.generateContent(prompt) }.text
                        ?: throw IllegalStateException("Empty stats response")
                }
                if (generation == statsGeneration) {
                    _readingStats.value = AiResult.Success(text)
                }
            } catch (e: TimeoutCancellationException) {
                if (generation == statsGeneration) {
                    _readingStats.value = AiResult.Failure(AiError.Timeout)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiRequestException) {
                if (generation == statsGeneration) {
                    _readingStats.value = AiResult.Failure(e.error)
                }
            } catch (e: Exception) {
                if (generation == statsGeneration) {
                    _readingStats.value = AiResult.Failure(AiError.Unknown(e.message))
                }
            } finally {
                if (generation == statsGeneration) {
                    _isAnalysingStats.value = false
                }
            }
        }
    }

    fun clearReadingStats() {
        _readingStats.value = null
    }

    fun generateSmartThemes(
        articles: List<Article>,
        preferredLanguage: String,
        onThemesGenerated: (Map<String, List<Article>>) -> Unit
    ) {
        if (articles.isEmpty()) return

        val generation = ++themesGeneration
        _isAnalysingSmartThemes.value = true
        _smartThemes.value = emptyMap()
        _selectedSmartTheme.value = null

        themesJob?.cancel()
        themesJob = scope.launch(Dispatchers.IO) {
            try {
                val numberedHeadlines = articles.mapIndexed { idx, art ->
                    "$idx: ${sanitizeForPrompt(art.title)}"
                }.joinToString("\n")

                val prompt = """
                    $PROMPT_INJECTION_GUARD

                    Group these news headlines into 3 to 6 logical, dynamic themes or storylines.
                    For each theme, provide a concise name and list the article indices belonging to it.
                    
                    Strict format:
                    [Theme Name]: index1, index2, index3
                    
                    Example:
                    Global Economy: 0, 4, 12
                    AI Breakthroughs: 2, 5

                    Do not include conversational filler, markdown formatting or explanations. Provide theme names in $preferredLanguage.

                    Headlines:
                    [[DATA]]
                    $numberedHeadlines
                    [[/DATA]]
                """.trimIndent()

                val text = aiRepository.cachedOrFetch(
                    cacheKey = AiCacheKeys.themes(
                        articleUrls = articles.map { it.url },
                        evidenceFingerprints = articles.map {
                            AiCacheKeys.articleEvidenceFingerprint(it.title, it.description, it.content)
                        },
                        language = preferredLanguage
                    ),
                    feature = AiFeature.THEMES
                ) {
                    withTimeout(90_000) { generativeModel.generateContent(prompt) }.text
                        ?: throw IllegalStateException("Empty smart themes response")
                }

                if (generation == themesGeneration) {
                    val parseResult = parseSmartThemesResponse(text, articles)
                    _smartThemes.value = parseResult.themes
                    onThemesGenerated(parseResult.themes)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == themesGeneration) {
                    errorEvents.emit("Could not group news themes right now.")
                }
            } finally {
                if (generation == themesGeneration) {
                    _isAnalysingSmartThemes.value = false
                }
            }
        }
    }

    fun selectSmartTheme(theme: String?) {
        _selectedSmartTheme.value = theme
    }

    fun analyzeNewsLocations(articles: List<Article>) {
        if (articles.isEmpty()) return

        val generation = ++locationsGeneration
        _isAnalysingLocations.value = true
        _newsLocations.value = emptyList()

        locationsJob?.cancel()
        locationsJob = scope.launch(Dispatchers.IO) {
            try {
                val numberedHeadlines = articles.mapIndexed { idx, art ->
                    "$idx: ${sanitizeForPrompt(art.title)} | ${sanitizeForPrompt(art.description)}"
                }.joinToString("\n")

                val prompt = """
                    $PROMPT_INJECTION_GUARD

                    Identify geographical locations mentioned in or relevant to these news stories.
                    For each location, output EXACTLY one line using this format:
                    INDEX|LOCATION_NAME|LATITUDE|LONGITUDE

                    Rules:
                    - INDEX must be the exact numeric index from the input.
                    - LOCATION_NAME is the city or country name.
                    - LATITUDE and LONGITUDE must be valid decimal coordinates.
                    - Max 15 locations total.
                    - Only include real, verifiable locations.
                    - Do not include conversational filler or headers.

                    [[DATA]]
                    $numberedHeadlines
                    [[/DATA]]
                """.trimIndent()

                val text = aiRepository.cachedOrFetch(
                    cacheKey = AiCacheKeys.locations(
                        articles.map { it.url },
                        articles.map { AiCacheKeys.articleEvidenceFingerprint(it.title, it.description, it.content) }
                    ),
                    feature = AiFeature.LOCATIONS
                ) {
                    withTimeout(90_000) { generativeModel.generateContent(prompt) }.text
                        ?: throw IllegalStateException("Empty location response")
                }

                if (generation == locationsGeneration) {
                    val parsed = parseLocationsResponse(text, articles)
                    _newsLocations.value = parsed.locations
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == locationsGeneration) {
                    errorEvents.emit("Could not load news map locations.")
                }
            } finally {
                if (generation == locationsGeneration) {
                    _isAnalysingLocations.value = false
                }
            }
        }
    }

    fun clearNewsLocations() {
        _newsLocations.value = emptyList()
    }

    fun setAnalysingInterests(analysing: Boolean) {
        _isAnalysingInterests.value = analysing
    }

    fun cancelAnalysis() {
        summaryGeneration++
        trendsGeneration++
        statsGeneration++
        themesGeneration++
        locationsGeneration++
        summaryJob?.cancel()
        trendsJob?.cancel()
        statsJob?.cancel()
        themesJob?.cancel()
        locationsJob?.cancel()
        _isSummarizing.value = false
        _isAnalysingInterests.value = false
        _isAnalysingStats.value = false
        _isAnalysingTrends.value = false
        _isAnalysingSmartThemes.value = false
        _isAnalysingLocations.value = false
    }
}
