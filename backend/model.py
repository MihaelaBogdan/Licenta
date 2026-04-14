import torch
import torch.nn as nn
from transformers import DistilBertModel


class IntentClassifier(nn.Module):
    """
    Intent classification model based on DistilBERT (multilingual).
    
    Architecture:
    - DistilBERT: Pre-trained transformer that understands 104 languages
      (including Romanian and English). Converts text into rich 
      contextual embeddings (768-dim vectors).
    - Classification Head: A simple feedforward network on top that
      maps the embeddings to intent categories.
    
    The DistilBERT weights are fine-tuned during training so the model
    learns to understand our specific chatbot domain.
    """
    
    MODEL_NAME = "distilbert-base-multilingual-cased"
    
    def __init__(self, num_classes, dropout_rate=0.3):
        super(IntentClassifier, self).__init__()
        
        # Pre-trained DistilBERT — understands 104 languages
        self.bert = DistilBertModel.from_pretrained(self.MODEL_NAME)
        hidden_size = self.bert.config.hidden_size  # 768
        
        # Classification head
        self.classifier = nn.Sequential(
            nn.Linear(hidden_size, 256),
            nn.ReLU(),
            nn.Dropout(dropout_rate),
            nn.Linear(256, 128),
            nn.ReLU(),
            nn.Dropout(dropout_rate),
            nn.Linear(128, num_classes)
        )
    
    def forward(self, input_ids, attention_mask):
        """
        Forward pass:
        1. Pass tokens through DistilBERT to get contextual embeddings
        2. Take the [CLS] token embedding (first token = sentence representation)
        3. Pass through classification head to get intent scores
        """
        # DistilBERT output
        outputs = self.bert(input_ids=input_ids, attention_mask=attention_mask)
        
        # Use [CLS] token representation (index 0) as sentence embedding
        cls_output = outputs.last_hidden_state[:, 0, :]
        
        # Classification
        logits = self.classifier(cls_output)
        return logits
