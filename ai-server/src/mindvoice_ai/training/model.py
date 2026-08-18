from __future__ import annotations

from typing import Any, Literal

from mindvoice_ai.settings import FEATURE_DIMENSION, SEQUENCE_LENGTH


Architecture = Literal["1d_cnn_v1", "bigru_v1", "temporal_cnn_v2"]


def build_baseline_model(
    class_count: int,
    *,
    architecture: Architecture = "1d_cnn_v1",
    learning_rate: float = 1e-3,
    dropout: float = 0.3,
    l2_weight_decay: float = 0.0,
) -> Any:
    if class_count < 2:
        raise ValueError("at least two classes are required")
    if architecture not in {"1d_cnn_v1", "bigru_v1", "temporal_cnn_v2"}:
        raise ValueError(f"unsupported architecture: {architecture}")
    if learning_rate <= 0:
        raise ValueError("learning_rate must be positive")
    if not 0.0 <= dropout < 1.0:
        raise ValueError("dropout must be in [0, 1)")
    if l2_weight_decay < 0:
        raise ValueError("l2_weight_decay cannot be negative")

    import tensorflow as tf

    regularizer = tf.keras.regularizers.L2(l2_weight_decay) if l2_weight_decay else None
    inputs = tf.keras.Input(
        shape=(SEQUENCE_LENGTH, FEATURE_DIMENSION), name="landmark_sequence"
    )
    x = tf.keras.layers.LayerNormalization(name="input_normalization")(inputs)
    if architecture == "1d_cnn_v1":
        x = tf.keras.layers.Conv1D(
            128, 5, padding="same", activation="relu", kernel_regularizer=regularizer
        )(x)
        x = tf.keras.layers.BatchNormalization()(x)
        x = tf.keras.layers.MaxPooling1D(2)(x)
        x = tf.keras.layers.Conv1D(
            128, 3, padding="same", activation="relu", kernel_regularizer=regularizer
        )(x)
        x = tf.keras.layers.BatchNormalization()(x)
        x = tf.keras.layers.Conv1D(
            64, 3, padding="same", activation="relu", kernel_regularizer=regularizer
        )(x)
        x = tf.keras.layers.GlobalAveragePooling1D()(x)
    elif architecture == "bigru_v1":
        x = tf.keras.layers.Bidirectional(
            tf.keras.layers.GRU(96, return_sequences=True, dropout=0.1)
        )(x)
        x = tf.keras.layers.Bidirectional(tf.keras.layers.GRU(64))(x)
    else:
        velocity = tf.keras.layers.Lambda(
            lambda values: tf.concat(
                (tf.zeros_like(values[:, :1, :]), values[:, 1:, :] - values[:, :-1, :]),
                axis=1,
            ),
            name="frame_velocity",
        )(x)
        x = tf.keras.layers.Concatenate(name="position_and_velocity")((x, velocity))
        x = tf.keras.layers.Conv1D(
            128, 3, padding="same", activation="relu", kernel_regularizer=regularizer
        )(x)
        x = tf.keras.layers.BatchNormalization()(x)
        x = tf.keras.layers.Conv1D(
            128,
            3,
            dilation_rate=2,
            padding="same",
            activation="relu",
            kernel_regularizer=regularizer,
        )(x)
        x = tf.keras.layers.BatchNormalization()(x)
        x = tf.keras.layers.MaxPooling1D(2)(x)
        x = tf.keras.layers.Conv1D(
            96, 3, padding="same", activation="relu", kernel_regularizer=regularizer
        )(x)
        average = tf.keras.layers.GlobalAveragePooling1D()(x)
        maximum = tf.keras.layers.GlobalMaxPooling1D()(x)
        x = tf.keras.layers.Concatenate()((average, maximum))
    x = tf.keras.layers.Dropout(dropout)(x)
    x = tf.keras.layers.Dense(96, activation="relu", kernel_regularizer=regularizer)(x)
    outputs = tf.keras.layers.Dense(
        class_count,
        activation="softmax",
        name="class_probabilities",
        kernel_regularizer=regularizer,
    )(x)

    model = tf.keras.Model(inputs=inputs, outputs=outputs, name=f"ksl_word_{architecture}")
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=learning_rate),
        loss="sparse_categorical_crossentropy",
        metrics=["accuracy"],
    )
    return model
