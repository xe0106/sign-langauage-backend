from __future__ import annotations

from typing import Any

from mindvoice_ai.settings import FEATURE_DIMENSION, SEQUENCE_LENGTH


def build_baseline_model(class_count: int) -> Any:
    if class_count < 2:
        raise ValueError("at least two classes are required")

    import tensorflow as tf

    inputs = tf.keras.Input(
        shape=(SEQUENCE_LENGTH, FEATURE_DIMENSION), name="landmark_sequence"
    )
    x = tf.keras.layers.LayerNormalization(name="input_normalization")(inputs)
    x = tf.keras.layers.Conv1D(128, 5, padding="same", activation="relu")(x)
    x = tf.keras.layers.BatchNormalization()(x)
    x = tf.keras.layers.MaxPooling1D(2)(x)
    x = tf.keras.layers.Conv1D(128, 3, padding="same", activation="relu")(x)
    x = tf.keras.layers.BatchNormalization()(x)
    x = tf.keras.layers.Conv1D(64, 3, padding="same", activation="relu")(x)
    x = tf.keras.layers.GlobalAveragePooling1D()(x)
    x = tf.keras.layers.Dropout(0.3)(x)
    x = tf.keras.layers.Dense(64, activation="relu")(x)
    outputs = tf.keras.layers.Dense(
        class_count, activation="softmax", name="class_probabilities"
    )(x)

    model = tf.keras.Model(inputs=inputs, outputs=outputs, name="ksl_word_1d_cnn")
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=1e-3),
        loss="sparse_categorical_crossentropy",
        metrics=["accuracy"],
    )
    return model
