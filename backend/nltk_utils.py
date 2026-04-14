import numpy as np
import nltk
from nltk.stem import WordNetLemmatizer
from nltk.corpus import stopwords
import string

# Download required NLTK data
nltk.download('punkt', quiet=True)
nltk.download('punkt_tab', quiet=True)
nltk.download('wordnet', quiet=True)
nltk.download('omw-1.4', quiet=True)
nltk.download('stopwords', quiet=True)

lemmatizer = WordNetLemmatizer()

# Romanian + English stopwords, but keep important ones for intent detection
STOP_WORDS = set(stopwords.words('english')) - {
    'not', 'no', 'where', 'what', 'when', 'how', 'who', 'which',
    'can', 'do', 'does', 'is', 'are', 'should', 'would', 'could',
    'need', 'want', 'like', 'help', 'tell', 'give', 'show'
}

# Punctuation to ignore
IGNORE_CHARS = set(string.punctuation)


def tokenize(sentence):
    """Tokenize the sentence into words."""
    return nltk.word_tokenize(sentence)


def lemmatize(word):
    """Lemmatize a word — better than stemming for preserving word meaning."""
    word = word.lower().strip()
    # Try as verb first, then noun
    lemma = lemmatizer.lemmatize(word, pos='v')
    if lemma == word:
        lemma = lemmatizer.lemmatize(word, pos='n')
    return lemma


def preprocess(sentence):
    """Full preprocessing pipeline: tokenize, clean, lemmatize."""
    tokens = tokenize(sentence)
    # Remove punctuation and stopwords, then lemmatize
    processed = []
    for w in tokens:
        w_lower = w.lower()
        if w_lower not in IGNORE_CHARS and w_lower not in STOP_WORDS:
            processed.append(lemmatize(w))
    return processed


def bag_of_words(tokenized_sentence, words):
    """
    Create a bag-of-words vector from a tokenized sentence.
    Each position represents whether a known word appears in the sentence.
    """
    sentence_words = [lemmatize(word) for word in tokenized_sentence]
    bag = np.zeros(len(words), dtype=np.float32)
    for idx, w in enumerate(words):
        if w in sentence_words:
            bag[idx] = 1.0
    return bag
